// QinhRuins 词缀脚本示例 —— 服主可写任意 JS 逻辑作为「词缀效果」
//
// ① 把本文件放在  QinhRuins/scripts/  下（首次启动已自动释放本示例）。
// ② 在 affixes.yml 里引用它：
//
//   affixes:
//     blood_ritual:
//       name: "§4血祭"
//       lore: ["§7秘境激活时降下血之考验"]
//       category: ENV
//       danger: 25            # 危险值（受 realm.tiers.danger-budget 约束）
//       reward: 20
//       min-tier: 3
//       effect:
//         type: script                                   # ★ 用脚本作为效果
//         script: "qinhruins:affix_example.js:onActivate" # 命名空间:文件:函数
//
// 脚本在【秘境激活瞬间】对在场每位玩家各执行一次。
//
// ★两个全局对象（来自 QinhCoreLib 脚本引擎）：
//   ctx  —— 上下文（读数据）：
//     ctx.player()                          当前玩家（可能为 null）
//     ctx.get("tier") / "affix" / "danger"  读取传入变量（层数 / 词缀id / 危险值）
//     ctx.set(key, value)                   写回变量
//     ctx.vars()                            取全部变量的快照
//   qcl  —— 动作（做事情）：
//     qcl.itemGive(ref, amount)             发物品（ref 支持 minecraft:/qinhitems:/mythicmobs: 等）
//     qcl.addPotion(目标, 类型, tick, 等级)  上药水效果（等级从 0 起：0=I 级）
//     qcl.heal(n) / qcl.damage(目标, n)     回血 / 造成伤害
//     qcl.buff(目标, key, 值, 运算, tick, 来源)  CoreLib 属性增益
//     qcl.economyDeposit(n, provider, currency) 发货币
//     qcl.runSyncLater(tick, function)      延迟在主线程执行
//     qcl.logInfo("...")                    打日志
//
// ⚠️ 注意：动作方法都在 qcl 上（不是 ctx）。写成 ctx.itemGive(...) 会报「方法不存在」。

function onActivate(ctx) {
    var player = ctx.player();
    if (!player) return;

    var tier = ctx.get("tier") || 1;
    var amp = Math.min(tier, 5) - 1;          // 层数越高，增益越强（药水等级从 0 起）

    // 给玩家上「迅捷」30 秒，等级随层数
    qcl.addPotion(player, "SPEED", 20 * 30, amp);

    // 高层额外奖励一个金苹果（示例：奖励与层数挂钩）
    if (tier >= 5) {
        qcl.itemGive("minecraft:golden_apple", 1);
    }

    player.sendMessage("§4[血祭] §7秘境注入了 §cT" + tier + " §7之力……");
    qcl.logInfo("[QR词缀] " + player.getName() + " 触发脚本词缀 T" + tier);
}

// 默认函数：引用里不写 :函数名 时调用 main
function main(ctx) {
    onActivate(ctx);
}
