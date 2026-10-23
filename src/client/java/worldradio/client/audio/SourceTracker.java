package worldradio.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import worldradio.Config;
import worldradio.WorldRadio;
import worldradio.item.PortableRadioItem;
import worldradio.item.Tuning;
import worldradio.net.Packets;
import worldradio.signal.Reception;
import worldradio.signal.SoundPlace;
import worldradio.signal.SourcePicker;
import worldradio.signal.SourcePicker.Candidate;
import worldradio.signal.VolumeCurve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Decides every client tick what the player hears: each playing radio the server listed for this dimension (loaded or
 * not, see {@link Packets.Sources}) is a candidate with its curve volume at the camera, and so is the portable radio in
 * the player's inventory when its station's signal arrives here; per station only the loudest place plays (with
 * hysteresis), and of the stations only the loudest few. A station that moves from one place to another keeps its
 * sound and stream. A radio a little out of earshot already plays at volume 0 when a place is free, so that its
 * stream runs before the player gets there.
 */
public final class SourceTracker {
    /** The latest source list from the server. */
    private static volatile Packets.Sources sources;
    private static final Map<String, Playing> PLAYING = new HashMap<>();
    private static final Map<String, Long> RETRY_AFTER = new HashMap<>();
    private static final SourcePicker PICKER = new SourcePicker();
    /** Ticks a new sound gets to show up in the sound engine before it counts as lost. */
    private static final int START_GRACE = 40;
    /** Blocks before a radio's hearing range at which its stream is opened, about four seconds of sprinting. */
    private static final double WARM_UP = 24.0;
    /** Source id of the portable radio the player carries. */
    private static final String PORTABLE = "portable";
    private static long ticks;

    private record Playing(RadioSoundInstance sound, long started) {
    }

    private SourceTracker() {
    }

    public static void setSources(Packets.Sources list) {
        sources = list;
    }

    /** The stations a portable radio can pick up where the camera is, strongest first. */
    public static List<Reception.Heard> receivable(Minecraft minecraft) {
        Packets.Sources list = sources;
        if (list == null || minecraft.level == null || !list.dimension().equals(minecraft.level.dimension().identifier())) {
            return List.of();
        }
        Vec3 ear = minecraft.gameRenderer.mainCamera().position();
        return Reception.at(ear.x, ear.y, ear.z, list.emitters());
    }

    /** The setting of the first portable radio in the inventory that is switched on and tuned, or null. */
    private static Tuning carried(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof PortableRadioItem)) continue;
            Tuning tuning = PortableRadioItem.tuning(stack);
            if (tuning.plays()) return tuning;
        }
        return null;
    }

    /** Where a radio's sound sits for a listener at {@code ear}: between the radio and its speakers, see {@link SoundPlace}. */
    private static Vec3 place(Vec3 ear, Vec3 centre, List<BlockPos> speakers) {
        if (speakers.isEmpty()) return centre;
        double[][] points = new double[speakers.size() + 1][];
        points[0] = new double[]{centre.x, centre.y, centre.z};
        for (int i = 0; i < speakers.size(); i++) {
            BlockPos s = speakers.get(i);
            points[i + 1] = new double[]{s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5};
        }
        double[] at = SoundPlace.blend(new double[]{ear.x, ear.y, ear.z}, points);
        return new Vec3(at[0], at[1], at[2]);
    }

    /** What is heard right now (not what is only kept ready): station address → position of the place it plays from. */
    public static Map<String, Vec3> playing() {
        Map<String, Vec3> out = new HashMap<>();
        PLAYING.forEach((url, p) -> {
            if (p.sound().loudness() > 0) out.put(url, p.sound().place());
        });
        return out;
    }

    /** Debug line for a playing station: whether the sound engine has it, and what its stream handed out. */
    public static String debug(String url) {
        Playing p = PLAYING.get(url);
        if (p == null) return "not playing";
        return "active=" + Minecraft.getInstance().getSoundManager().isActive(p.sound()) + " " + p.sound().streamStats()
                + " category=" + p.sound().getSource().getName();
    }

    public static float volumeOf(String url) {
        Playing p = PLAYING.get(url);
        return p == null ? 0 : p.sound().loudness();
    }

    public static void tick(Minecraft minecraft) {
        ticks++;
        StreamPool.tick();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            stopAll();
            sources = null;
            return;
        }
        Vec3 ear = minecraft.gameRenderer.mainCamera().position();
        List<Candidate> candidates = new ArrayList<>();
        Map<String, Vec3> places = new HashMap<>();
        Map<String, Vec3> warm = new HashMap<>();
        Packets.Sources list = sources;
        if (list != null && list.dimension().equals(level.dimension().identifier())) {
            for (Packets.RadioSource radio : list.receivers()) {
                Vec3 centre = Vec3.atCenterOf(radio.pos());
                double distance = centre.distanceTo(ear);
                // not heard yet but close: it plays silently, so the sound is there and fades in when the player
                // walks into its range instead of setting in when the stream has connected
                if (distance > VolumeCurve.hearingRange(radio.range()) + WARM_UP) continue;
                double volume = VolumeCurve.hearing(distance, radio.range());
                if (volume <= 0) {
                    Vec3 known = warm.get(radio.url());
                    if (known == null || distance < known.distanceTo(ear)) warm.put(radio.url(), centre);
                    continue;
                }
                String id = "r" + radio.pos().asLong();
                candidates.add(new Candidate(radio.url(), id, volume * radio.volume()));
                places.put(id, place(ear, centre, radio.speakers()));
            }
            // the portable radio plays at the player, as loud as its station's signal arrives here
            Tuning carried = carried(minecraft.player);
            if (carried != null) {
                // ponytail: scans every emitter each tick; cache per block position if dimensions get thousands of them
                double strength = Reception.strength(ear.x, ear.y, ear.z, carried.url(), list.emitters());
                if (strength > 0) {
                    candidates.add(new Candidate(carried.url(), PORTABLE, strength * carried.volume()));
                    places.put(PORTABLE, ear);
                }
            }
        }
        Map<String, Candidate> chosen = PICKER.pick(candidates, Config.get().maxStations());
        // places the heard stations leave free go to the ones kept ready, at volume 0
        for (Map.Entry<String, Vec3> e : warm.entrySet()) {
            if (chosen.size() >= Config.get().maxStations()) break;
            if (chosen.containsKey(e.getKey())) continue;
            String id = "w" + e.getKey();
            chosen.put(e.getKey(), new Candidate(e.getKey(), id, 0.0));
            places.put(id, e.getValue());
        }

        for (Iterator<Map.Entry<String, Playing>> it = PLAYING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Playing> e = it.next();
            Playing p = e.getValue();
            boolean lost = ticks - p.started() > START_GRACE && !minecraft.getSoundManager().isActive(p.sound());
            if (!chosen.containsKey(e.getKey()) || lost) {
                minecraft.getSoundManager().stop(p.sound());
                p.sound().finish();
                it.remove();
                if (lost) {
                    RETRY_AFTER.put(e.getKey(), ticks + 100);
                    WorldRadio.LOGGER.info("Radio: sound for {} was dropped by the sound engine, retrying", e.getKey());
                }
            }
        }
        for (Candidate c : chosen.values()) {
            Vec3 place = places.get(c.sourceId());
            Playing p = PLAYING.get(c.station());
            if (p != null) {
                p.sound().move(place, c.sourceId().equals(PORTABLE));
                p.sound().setVolume((float) c.volume());
                continue;
            }
            if (RETRY_AFTER.getOrDefault(c.station(), 0L) > ticks) continue;
            RadioSoundInstance sound = new RadioSoundInstance(c.station(), place, (float) c.volume(),
                    c.sourceId().equals(PORTABLE));
            minecraft.getSoundManager().play(sound);
            PLAYING.put(c.station(), new Playing(sound, ticks));
            WorldRadio.LOGGER.info("Radio: playing {} from {}", c.station(), c.sourceId());
        }
    }

    public static void stopAll() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Playing p : PLAYING.values()) {
            minecraft.getSoundManager().stop(p.sound());
            p.sound().finish();
        }
        PLAYING.clear();
    }
}
