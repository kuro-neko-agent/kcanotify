# PR Review: `feature/expedition-gs-rate` — 远征大成功率显示

## 变更概要

本 PR 为 kcanotify 的远征检查浮窗（`KcaExpeditionCheckViewService`）添加了以下功能：

1. **大成功率计算与显示** — 根据远征类型（普通/旗舰/ドラム缶）计算大成功概率
2. **闪光状态概览** — 显示 ☆sparkled/required 计数
3. **条件清单** — 每行独立着色（绿=满足/红=未满足）的 GS 条件描述
4. **大发系装备加成更新** — 新增武装大发(409)、AB 艇(408)、北非二号(436)、特大发+一式砲(449) 的收益计算
5. **i18n** — 6 种语言（en/ja/ko/zh-CN/zh-TW/es）的字符串资源
6. **布局** — 新增 `view_excheck_gs_rate` 和 `view_excheck_gs_desc` XML 区块

---

## 🐛 Bug 与正确性问题

### Bug 1（严重）：改良星级加成公式与 poi 不一致

**kcanotify 实现：**
```java
// B_star: improvement level bonus
int total_dlc = daihatsu_count + toku_count + armed_daihatsu_count + tank_count + ab_boat_count + amp_count;
if (total_dlc > 0) {
    bonus_default += bonus_default * bonus_level / 100.0;
}
```

这里 `bonus_level` 是所有大发系装备的**改良星级之和**，公式为 `B1 * Σstar / 100`。

**poi 实现：**
```js
const aveImp = dhtCount === 0 ? 0 : impLvlCount / dhtCount
const bStar = b1 * aveImp / 100
```

poi 用的是**平均改良星级** (`impLvlCount / dhtCount`)，不是星级之和。

**影响：** kcanotify 的 B_star 会随装备数量倍增。例如 4 个 ★5 大发：
- poi: `B1 * (20/4) / 100 = B1 * 5/100` ✅
- kcanotify: `B1 * 20 / 100 = B1 * 20/100` ❌（4倍偏差）

**修复：**
```java
if (total_dlc > 0) {
    double avg_level = bonus_level / total_dlc;
    bonus_default += bonus_default * avg_level / 100.0;
}
```

### Bug 2（严重）：Kinu K2 未计入 bonus_count

`getBonusInfo()` 中 `kinu_exist` 设为 `true`，但 `bonus_count` 未 +1。在 `calculateBonusValue()` 中 kinu 参与 B1 计算但不计入 `total_dlc`（用于 B_star 的除数），导致平均改良星级计算不包含 Kinu 的贡献。

这与 poi 一致（poi 的 `spShipCount` 也不算入 `dhtCount`），所以**在修正 Bug 1 后，这点是正确的**。但目前 kinu 的存在影响了 B1 = min(0.2, b1_before_cap) 而不影响 `total_dlc` 用于除法——这正好是对的。

**结论：不是 bug，但建议加注释说明。**

### Bug 3（中等）：缺少特大发动艇（Toku Daihatsu）的额外加成 B2

poi 有独立的 `computeTokuBonus()` 函数，根据特大发数量和普通大发数量查表计算额外加成（B2），这是一个独立于 B1 的非线性 bonus table。

**kcanotify 只计算了 B1 + B_star，完全没有 B2（toku bonus table）。**

虽然 kcanotify 原代码有 `bonus_toku` 和 `toku_bonus_table`，但这个表的值和逻辑是旧的。poi 的表更精确（来自 NGA 数据）。

**影响：** 有 3+ 特大发时，收益计算偏低（缺少 2%~6% 的额外加成）。

### Bug 4（中等）：GS Rate 公式除数 0.99 vs 0.0099

**kcanotify:**
```java
rate = (sparkledCount * 15 + 20) / 0.99;
```
结果单位为"百分比"（如 100.0 = 100%）。

**poi:**
```js
gsRate = Math.round((shipsCount * 15 + 20) / 0.0099) / 100
```
poi 先除以 0.0099 得到万分比，round 后除以 100 得到百分比。

**数学等价性分析：**
- poi: `Math.round(X / 0.0099) / 100` = `Math.round(X * 10000/99) / 100`
- kcanotify: `Math.round(X / 0.99 * 100) / 100` = `Math.round(X * 100/99) / 100`

**这两个不等价！** poi 的 round 在更高精度上执行。

例：6 艘全闪 → X = 6*15+20 = 110
- poi: `Math.round(110/0.0099)/100 = Math.round(11111.11)/100 = 11111/100 = 111.11`
- kcanotify: `Math.round(110/0.99*100)/100 = Math.round(11111.11)/100 = 111.11`

这个特定例子恰好相同，但浮点边界情况可能有差异。

**建议：** 使用 poi 的方式 (`Math.round(X / 0.0099) / 100`) 以确保一致性。

### Bug 5（低）：GS_TYPE_NORMAL 下 drumCount=0 且 sparkled ≠ total 时 rate=0 无 ✓/✗ 输出

在 `getGreatSuccessDescription` 的 `GS_TYPE_NORMAL` 分支中，如果 `total >= reqSparkle` 但 `notSparkled > 0`，只输出未闪光信息，没有"all sparkled"的正面确认，这是正确的。但如果 `total < reqSparkle && notSparkled == 0`（理论上不可能因为 total < 6 但全闪），逻辑上没问题。

**不是 bug，只是 edge case 确认。**

---

## ⚠️ Edge Case 问题

### EC1: `ship_data` 为 null 或空

- `calculateGreatSuccessRate()`: 有 `if (ship_data == null || ship_data.isEmpty()) return -1` ✅
- `getSparkledCount()`: 有 `if (ship_data == null) return 0` ✅
- `getRequiredSparkleForGS()`: 有 `ship_data != null ? ship_data.size() : 0` ✅
- `getGreatSuccessDescription()`: ⚠️ **`ship_data` 为 null 时，`total=0, sparkled=0, notSparkled=0`**，NORMAL 分支会输出 "Fleet 0 ships (need 6 for 100%)" 然后跳过 sparkle 信息。可接受。

### EC2: 舰船没有装备 (item 数组为空)

`calculateGreatSuccessRate()` 中 `ship.getAsJsonArray("item")` 如果返回空数组，for-each 不会执行。✅ 安全。

### EC3: 旗舰等级为 0

不太可能但 `Math.sqrt(0) + 0/10 = 0`，公式退化为 `(sparkled*15 + 15) / 0.99`。✅ 安全。

---

## 🔧 代码质量问题

### CQ1: 重复的 drum 计数逻辑

`calculateGreatSuccessRate()` 和 `getGreatSuccessDescription()` 中都有独立的 drum 计数循环。应抽取为共享方法或缓存。

### CQ2: 多余的空行

diff 中有几处新增的空行（如 `boolean isGreatSuccess = false;` 后面多了 3 个空行），应清理。

### CQ3: SpannableStringBuilder 的 ForegroundColorSpan 未回收

每次调用 `buildGreatSuccessDescription()` 都创建新的 `ForegroundColorSpan` 对象。在高频更新场景下可能产生 GC 压力。实际影响较小（远征检查非高频操作）。

### CQ4: `joinStr` 方法未在 diff 中显示

代码引用了 `joinStr(lines, "\n")` 和 `joinStr(names, ", ")`，假设这是已有的工具方法。需确认其存在。

### CQ5: 硬编码远征列表

`GS_DRUM_EXPEDS` 和 `GS_FLAGSHIP_EXPEDS` 是硬编码的。如果游戏更新新远征，需要手动修改代码。poi 也是如此，所以这是可接受的。

---

## 🌐 i18n 问题

### i18n-1（Bug）：`excheckview_gs_desc_need_ships` 在 ja 和 ko 缺失

`values/strings.xml`、`values-zh-rCN/`、`values-zh-rTW/`、`values-es/` 都有此字符串，但 **`values-ja/` 和 `values-ko/` 缺失**。

运行时日文/韩文 locale 会 fallback 到默认英文值，不会崩溃但不一致。

**建议添加：**
```xml
<!-- values-ja -->
<string name="excheckview_gs_desc_need_ships" formatted="false">編成%d隻 (100%%には%d隻必要)</string>

<!-- values-ko -->
<string name="excheckview_gs_desc_need_ships" formatted="false">편성 %d척 (100%%에 %d척 필요)</string>
```

### i18n-2（小问题）：zh-CN/zh-TW 中ドラム缶未翻译

```xml
<string name="excheckview_gs_desc_drum_count">ドラム缶 ×%d ...</string>
```

中文里应该用"ドラム缶"还是"桶"？原 app 其他地方用 `drum_count` 标签似乎也是日文，保持一致即可，但如果要本地化更彻底，建议用"ドラム缶(运输桶)"。

### i18n-3：es 的 GS 描述字符串全是英文

西班牙语的 GS 描述字符串直接复制了英文值，没有翻译。虽然可工作，但不一致。

---

## 📱 UI/UX 问题

### UI1: 条件编号自动递增

`buildGreatSuccessDescription()` 给每个"主条件"自动编号（1, 2, 3...），这挺清晰的。✅

### UI2: 无障碍性（Accessibility）

`SpannableStringBuilder` 的颜色信息对 TalkBack 不可见。屏幕阅读器用户无法区分满足/未满足的条件。

**建议：** 保留 ✓/✗ 前缀在显示文本中（目前被 strip 掉了），或添加 `contentDescription`。

### UI3: 颜色对比度

使用 `colorExpeditionBtnGoodBack`（绿）和 `colorExpeditionBtnFailBack`（红）作为文字色。需确认这些颜色在深色背景上有足够对比度。如果这些颜色原本是按钮背景色，用作文字色可能偏暗。

---

## ⚡ 性能

### P1: 整体评估

GS 率计算在 `setItemViewLayout()` 中执行，只在用户查看远征详情时触发。复杂度 O(ships * equips)，完全可接受。无性能问题。

### P2: 重复遍历

drum 计数在 `calculateGreatSuccessRate()` 和 `getGreatSuccessDescription()` 中各遍历一次。可以缓存但影响微乎其微。

---

## 🔄 poi 对比表

| 特性 | poi-plugin-ezexped | kcanotify (本 PR) | 差异 |
|---|---|---|---|
| **GS 类型分类** | Normal / Drum / Flagship | Normal / Drum / Flagship | ✅ 一致 |
| **远征列表 (Drum)** | 21,24,37,38,40,44,142 | 21,24,37,38,40,44,142 | ✅ 一致 |
| **远征列表 (Flagship)** | 101-105,112-115,41,43,45,46,32,131-133,141 | 同上 | ✅ 一致 |
| **Drum 参数** | {21:[3,4], 24:[0,2], ...} | 同上 | ✅ 一致 |
| **Normal 公式** | `(n*15+20)/0.0099` (全闪时) | `(n*15+20)/0.99` | ⚠️ 精度差异 |
| **Flagship 公式** | `(sparkle*15+15+floor(sqrt(lv)+lv/10))/0.0099` | `(sparkle*15+15+floor(sqrt(lv)+lv/10))/0.99` | ⚠️ 精度差异 |
| **Drum 公式** | 同上三段式(40/20/5) | 同上三段式 | ⚠️ 精度差异 |
| **Normal: 非全闪 rate** | 0 | 0 | ✅ 一致 |
| **Drum: drums < min 且 min>0** | 0 | 0 | ✅ 一致 |
| **B1 大发基础加成** | 5%/3%/2%/1% 分桶 | 5%/3%/2%/1% 分桶 | ✅ 一致 |
| **B_star 改良加成** | `B1 * avgStar / 100` | `B1 * sumStar / 100` | ❌ **Bug** |
| **B2 特大发额外加成** | 独立查表 (0%~6%) | 有旧表但逻辑可能过时 | ⚠️ 需验证 |
| **武装大发 (409)** | 3% | 3% | ✅ 一致 |
| **AB 艇/北非/一式砲 (408,436,449)** | 2% | 2% | ✅ 一致 |
| **Kinu K2 加成** | 5% (不计入 dhtCount) | 5% (不计入 total_dlc) | ✅ 一致 |
| **GSHigherLevel 提示** | 独立 ereq 检查最高等级舰 | 集成在 Flagship desc 中 | ✅ 功能等价 |
| **AllSparkled 检查** | 独立 ereq | 集成在描述中 | ✅ 功能等价 |
| **显示格式** | 简洁百分比 + tooltip | 百分比 + 编号条件列表 + 着色 | kcanotify 更详细 |
| **未闪光舰名显示** | 无 | ✅ 有（含舰名和等级） | kcanotify 独有功能 |
| **需要闪光数提示** | tooltip 中显示 | ☆X/Y 格式 | 两种方式各有优劣 |

---

## 📋 推荐修复优先级

### P0（必须修复）
1. **B_star 公式**：`bonus_level / 100.0` → `(bonus_level / total_dlc) / 100.0`（使用平均星级）

### P1（强烈建议）
2. **GS Rate 精度**：`/ 0.99` → 使用 `Math.round(X / 0.0099) / 100.0` 与 poi 保持一致
3. **ja/ko 缺失字符串**：添加 `excheckview_gs_desc_need_ships`

### P2（建议改进）
4. **Drum 计数去重**：抽取共享方法避免重复遍历
5. **无障碍性**：保留 ✓/✗ 在显示文本中或添加 contentDescription
6. **zh-CN/zh-TW 翻译**：ドラム缶加注中文释义
7. **清理多余空行**

### P3（可选）
8. **es 字符串翻译**：将英文 GS 描述翻译为西班牙语
9. **B2 Toku bonus table** 与 poi 对齐（但这是预先存在的问题，不在本 PR scope）

---

## 总结

本 PR 功能设计合理，远征分类和参数与 poi 完全一致，UI 呈现比 poi 更详细友好（显示未闪光舰名、条件着色）。主要问题是 **B_star 改良加成公式用了星级之和而非平均值**，这会导致多装备时收益计算显著偏高。GS Rate 的除数精度差异在大多数情况下不影响结果但建议统一。i18n 有两个语言缺失一个字符串。整体完成度很高，修复 P0 和 P1 后即可合并。
