package io.github.steamwork.content.line;

/**
 * 产线内需要手动触发才能完成一次加工周期的机器可实现此接口。
 *
 * <p>当产线入口安装了"自动生产模组（Auto-Production Module）"升级后，
 * 入口在每次 tick 时会对产线内所有实现了本接口的成员调用 {@link #performAutoInteract()}，
 * 替代玩家的手动右键操作。</p>
 *
 * <p>典型应用场景：需要玩家手动右键触发一次配方周期的机器（如摇柄压机、手动磨石、
 * 需要按键才能推进的 Pylon 机器等）。实现类应在 {@link #performAutoInteract()} 内
 * 完成单次触发所需的全部逻辑，并自行维护内部冷却计数器以控制触发节奏。</p>
 */
public interface ManualInteractMember extends ProductionLineMember {

    /**
     * 执行一次自动交互，等效于玩家手动右键操作一次。
     *
     * <p>入口每 tick 调用一次；实现类若需限制频率，应在内部记录上次触发 tick 并自行跳过。</p>
     */
    void performAutoInteract();
}
