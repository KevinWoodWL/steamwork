package io.github.steamwork.content.machines;

/**
 * Steamwork 内部接口：标识机器可被蒸汽涡轮加速。
 *
 * <p>之所以不直接复用 Rebar 的 {@code ProcessorRebarBlock}：
 * Rebar 0.37 的 {@code ProcessorRebarBlock$Companion.onLoad} 会在反序列化失败的方块上抛 NPE
 * （chunk load → onLoad 用 {@code processorBlocks[block]!!}，旧 PDC 缺字段时为 null）。
 * 自定义一个轻量接口可以彻底绕开这个 bug 路径，同时保留"涡轮加速自家机器"的玩法。</p>
 */
public interface SteamBoostable {

    /**
     * 由蒸汽涡轮调用，推进机器内部进度若干 tick。
     * 不消耗本机器的蒸汽 buffer（消耗的是涡轮自己的蒸汽）。
     *
     * @param ticks 推进的 tick 数
     */
    void boostProcess(int ticks);
}
