package worldradio.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One more station for a transmitter: tuned and switched like the transmitter, but it sends nothing by itself. A
 * transmitter it is connected to (touching it, or through a chain of touching channels) sends its station too, with
 * the transmitter's antenna and range.
 */
public class ChannelBlock extends RadioBlock {

    public ChannelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChannelBlockEntity(pos, state);
    }

    @Override
    protected boolean takesAntenna() {
        return false;
    }
}
