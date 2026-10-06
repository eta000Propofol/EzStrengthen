# EzStrengthen

[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![CI](https://github.com/eta000Propofol/EzStrengthen/actions/workflows/ci.yml/badge.svg)](https://github.com/eta000Propofol/EzStrengthen/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/eta000Propofol/EzStrengthen?sort=semver)](https://github.com/eta000Propofol/EzStrengthen/releases/latest)
[![Server](https://img.shields.io/badge/Paper-26.1.2-8A2BE2)](https://papermc.io/downloads)
[![Java](https://img.shields.io/badge/Java-25%2B-orange)]()

> 适用于 Paper 26.1.2（Java 25+）的装备强化插件：箱子 GUI 操作，Vault 经济结算，随机词条强化。

## 简介

通过箱子 GUI 强化任意物品，每次强化消耗 Vault 货币，第 5、6 次额外消耗「至纯源石」，
每次强化随机获得一个词条（等级 1~5，可重复），最多强化 6 次。
强化效果仅在物品位于主手、副手或身穿时生效。

## 功能特性

- 箱子 GUI 强化任意物品，最多 6 次
- 22 种战斗词条，等级 1~5，可重复
- Vault 货币结算，费用逐级递增（可配置）
- 第 5、6 次强化额外消耗「至纯源石」
- 修复耐久与重置属性
- 配置热重载，现存装备描述自动刷新

## 环境依赖

| 依赖 | 说明 |
| --- | --- |
| [Paper 26.1.2](https://papermc.io/downloads) | 服务端（需 Java 25+） |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | 经济接口，需要已注册的经济实现（如 EssentialsX / CMI 等） |

## 安装

1. 服务器安装 Paper 26.1.2（需 Java 25+）。
2. 安装 Vault 以及任意经济插件（如 EssentialsX / CMI / 其他支持 Vault 的经济插件）。
3. 从 [Releases](https://github.com/eta000Propofol/EzStrengthen/releases/latest) 下载 `EzStrengthen-<版本>.jar`，放入服务器的 `plugins` 文件夹。
4. 启动服务器，插件会自动生成 `plugins/EzStrengthen/config.yml`。
5. 修改配置后执行 `/st reload` 重载。

## 命令

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/st`、`/strengthen` | 打开装备强化界面：把要强化的物品放进中间格子，点击绿宝石开始强化；关闭界面时物品自动返还背包 | `ezstrengthen.use`（默认所有人可用） |
| `/st reload` | 重载配置 | `ezstrengthen.admin` |
| `/st give <玩家> <数量>` | 给玩家发放至纯源石 | `ezstrengthen.admin` |
| `/st stamp <玩家> <词条id:等级> ...` | 把操作者手持的物品按指定词条生成强化版并交给目标玩家，用于找回丢失的强化装备 | `ezstrengthen.admin` |

## 玩法规则

- 任意物品都可以强化，最多 6 次；每次强化固定消耗货币（费用逐级递增，可配置）。
- 第 5、6 次强化额外消耗 1 个至纯源石（数量可配置）。
- 强化有失败概率（默认 90/80/70/60/50/40%），失败只消耗材料，不降级不清空。
- 每次成功强化随机增加一个词条（22 种，可重复），词条等级 1~5，
  等级越高越强力、出现的概率越低（默认权重 50/30/10/7/3）。
- 触发概率类词条：Lv1 为 5%，Lv5 为 20%，随等级递增（可在 config.yml 调整）。
- 修改配置并执行 `/st reload` 后，现存强化装备的描述会按新数值自动刷新。
- 修复耐久与重置属性：每次各消耗 5000 货币（可配置），重置会清空全部强化词条。
- 荆棘附魔（含守卫者）的反伤同样享受词条加成。
- 至纯源石：击杀末影龙掉落（默认每只 1 个），物品本体由荧石改造而来。

## 词条列表

真实伤害、物理伤害、物理防御、暴击几率、暴击伤害、冰冻几率、流血几率、致盲几率、
闪避几率、漂浮几率、反弹几率、远程伤害、近战伤害、远程防御、近战防御、眩晕几率、
雷击几率、虚弱几率、击飞几率、混乱几率、雷击伤害、斩杀几率。

每个词条都有实际战斗效果，具体数值与持续时间可在 `config.yml` 中调整。

## 构建

需要 JDK 25+；首次构建会联网下载依赖。

```powershell
# Windows
gradlew.bat build
```

```bash
# Linux/macOS
./gradlew build
```

单元测试位于 `src/test/java`，可单独执行：`gradlew test`（Windows）或 `./gradlew test`。

产物位于 `build/libs/EzStrengthen-<版本>.jar`。

## License

本项目基于 [Apache-2.0](LICENSE) 协议开源。
