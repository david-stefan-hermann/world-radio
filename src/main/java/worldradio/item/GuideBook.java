package worldradio.item;

import bcf.guidebook.Book;
import bcf.guidebook.BookTheme;
import bcf.guidebook.Chapter;
import bcf.guidebook.Section.Break;
import bcf.guidebook.Section.Heading;
import bcf.guidebook.Section.ItemLine;
import bcf.guidebook.Section.Pictures;
import bcf.guidebook.Section.Quote;
import bcf.guidebook.Section.Recipe;
import bcf.guidebook.Section.Text;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import worldradio.Config;
import worldradio.WorldRadio;
import worldradio.net.Packets;

import java.util.List;
import java.util.Map;

/**
 * The content of the guide book, as data: chapters of sections, which the screen of bcf-guide-book-lib wraps and
 * breaks into pages. Texts are translation keys {@code worldradio.book.<key>} in the language files; the screen checks
 * every key the book uses against the loaded language in the dev environment.
 */
public final class GuideBook {
    /**
     * The tuner's teak case around one sheet of paper (textures/gui/book.png, made by tools/MakeBook.java), tabs in the
     * paper's colour and ivory keys to turn the pages. A recipe's tag ingredient shows one of its kinds; the page says
     * that any will do.
     */
    public static final Book BOOK = Book.of(WorldRadio.MOD_ID, BookTheme.builder()
            .texture(WorldRadio.id("textures/gui/book.png"))
            .panel(0xFF8A5A32, 0xFFA78365, 0xFF4C311B).border(0xFF160C05).tabs(0xFFF1E6CC, 0xFFFBF5E6)
            .page(0xFFF1E6CC, 0xFFD9C9A2).ink(0xFF4A2A12, 0xFFB45A14, 0xFF2A2018, 0xFF7A6A52)
            .slots(0xFFE4D6B4, 0xFFB8A67C, 0xFF8A6A30)
            .buttons(0xFFF4EEE0, 0xFFFFFFFF, 0xFFB9AE96, 0xFF160C05, 0xFFFFFFFF, 0xFFB8A67C, 0xFF1A1A1A, 0xFF6A665E)
            .pageNumber(0xFFFFF4DC).build(), GuideBook::chapters)
            .standIns(Map.of("#minecraft:planks", "minecraft:oak_planks", "#minecraft:wool", "minecraft:white_wool"));

    private GuideBook() {
    }

    public static String key(String name) {
        return BOOK.key(name);
    }

    /**
     * Built when the book opens: item stacks need the registries. The numbers are those of this game's config, which
     * is the server's own in single player (as on the screens' range line).
     */
    public static List<Chapter> chapters() {
        Config.Values config = Config.get();
        Heading crafting = new Heading(key("heading.crafting")), use = new Heading(key("heading.use"));
        Break turn = new Break(); // every block: what it is and its recipe on the first page, its use on the second
        return List.of(
                new Chapter(key("start"), new ItemStack(WorldRadio.GUIDE_BOOK_ITEM), Chapter.Side.RIGHT, List.of(
                        new Text(key("start.text")), new Quote(key("start.motto")),
                        new Heading(key("heading.steps")),
                        new ItemLine(List.of(new ItemStack(WorldRadio.RADIO_ITEM)), key("start.step1")),
                        new ItemLine(List.of(vanilla("lightning_rod")), key("start.step2")),
                        new ItemLine(List.of(new ItemStack(WorldRadio.RECEIVER_ITEM)), key("start.step3")))),
                new Chapter("block.worldradio.radio", new ItemStack(WorldRadio.RADIO_ITEM), Chapter.Side.LEFT, List.of(
                        new Text(key("transmitter.text")), new Quote(key("transmitter.motto")),
                        crafting, recipe("radio"),
                        turn, use, new Text(key("transmitter.use")), new Text(key("transmitter.more")))),
                new Chapter("block.worldradio.channel", new ItemStack(WorldRadio.CHANNEL_ITEM), Chapter.Side.LEFT, List.of(
                        new Text(key("channel.text")), new Quote(key("channel.motto")),
                        crafting, recipe("channel"),
                        turn, picture("channels"),
                        use, new Text(key("channel.use"), Packets.MAX_STATIONS))),
                new Chapter("block.worldradio.amplifier", new ItemStack(WorldRadio.AMPLIFIER_ITEM), Chapter.Side.LEFT, List.of(
                        new Text(key("amplifier.text")), new Quote(key("amplifier.motto")),
                        crafting, recipe("amplifier"),
                        turn, picture("relay"),
                        use, new Text(key("amplifier.use")))),
                new Chapter("block.worldradio.receiver", new ItemStack(WorldRadio.RECEIVER_ITEM), Chapter.Side.LEFT, List.of(
                        new Text(key("receiver.text")), new Quote(key("receiver.motto")),
                        crafting, recipe("receiver"), new Text(key("receiver.any")),
                        turn, use, new Text(key("receiver.use"), config.hearingBase()), new Text(key("receiver.silent")),
                        new Heading(key("heading.direction")), new Text(key("receiver.direction")))),
                new Chapter("block.worldradio.speaker", new ItemStack(WorldRadio.SPEAKER_ITEM), Chapter.Side.LEFT, List.of(
                        new Text(key("speaker.text"), config.speakerStep(), config.hearingMax(), config.speakerVolume()),
                        new Quote(key("speaker.motto")),
                        crafting, recipe("speaker"), new Text(key("speaker.any")),
                        turn, picture("speakers"),
                        use, new Text(key("speaker.use")))),
                new Chapter("item.worldradio.portable_radio", new ItemStack(WorldRadio.PORTABLE_RADIO_ITEM), Chapter.Side.LEFT, List.of(
                        new Text(key("portable.text")), new Quote(key("portable.motto")),
                        crafting, recipe("portable_radio"), new Text(key("portable.item")),
                        turn, use, new Text(key("portable.use")),
                        new Heading(key("heading.place")), new Text(key("portable.place"), config.hearingBase()))),
                new Chapter(key("range"), vanilla("lightning_rod"), Chapter.Side.RIGHT, List.of(
                        new Text(key("range.text"), config.baseRange(), config.antennaStep(), config.maxAntenna(),
                                Config.range(config.maxAntenna())),
                        new Quote(key("range.motto")),
                        picture("tower"),
                        new Heading(key("heading.antenna")),
                        new ItemLine(List.of(vanilla("lightning_rod"), vanilla("iron_bars"), vanilla("iron_chain"),
                                vanilla("end_rod")), key("range.blocks")),
                        new Text(key("range.place")),
                        new Heading(key("heading.display")), new Text(key("range.display")),
                        new Text(key("range.server")))),
                new Chapter(key("stations"), vanilla("spyglass"), Chapter.Side.RIGHT, List.of(
                        new Text(key("stations.text")), new Quote(key("stations.motto")),
                        new Heading(key("heading.favorites")), new Text(key("stations.favorites")),
                        new Heading(key("heading.browse")), new Text(key("stations.browse")),
                        new Heading(key("heading.url")), new Text(key("stations.url")),
                        new Heading(key("heading.streams")), new Text(key("stations.streams")))),
                new Chapter(key("listening"), vanilla("note_block"), Chapter.Side.RIGHT, List.of(
                        new Text(key("listening.text")), new Quote(key("listening.motto")),
                        new Heading(key("heading.volume")), new Text(key("listening.volume")),
                        new Heading(key("heading.overlap")), new Text(key("listening.overlap"), config.maxStations()))));
    }

    /** By id: several vanilla items are no plain fields of Items any more (the copper and colour families). */
    private static ItemStack vanilla(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(name)));
    }

    private static Pictures picture(String name) {
        return BOOK.picture(name, 96, 96, false);
    }

    private static Recipe recipe(String name) {
        return BOOK.recipe(name);
    }
}
