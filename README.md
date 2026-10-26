# 蓝牙编码查看器（BtCodecViewer）

一个只有 17KB 的安卓小工具，用来查看当前蓝牙耳机/音箱 **协商好的**：
- 编码（SBC / AAC / aptX / aptX HD / LDAC / LHDC / LC3 / Opus）
- 采样率（44.1k ~ 192k）
- 位深（16 / 24 / 32 bit）
- 声道
- 码率档位（标称，如 LDAC 990k、LHDC 音质优先档）
- 厂商 codecSpecific 原始字段（排查 LHDC 档位用）

## 安装
1. 把 `BtCodecViewer.apk` 发到手机（QQ/微信/数据线/网盘都行）
2. 点开安装，系统提示"未知来源"就允许这一次
3. 打开 App，允许它的**蓝牙连接**权限（Android 12+ 必须给）
4. 连上耳机、开始放歌，点「刷新」；想盯着看就开「自动刷新」

## 关于"动态实时 kbps"，必须说实话
- Android 系统只开放**协商结果**（用的是什么编码、什么档位），
  **不开放逐秒跳动的实时码率**。
- LHDC/LDAC 遇到干扰会自动降速（900→600→400k），这个过程
  非 root 手机上任何 App 都读不到具体数字，付费的也一样。
- 想看真实曲线，只有两条路：
  1. root 手机；
  2. 电脑 adb 抓蓝牙 HCI 日志，用 Wireshark 解析。
- 本 App 里如果出现"系统拒绝返回编码状态"，说明你这台 ROM
  把隐藏接口封了，这是系统限制，不是 App 问题。

## 从源码自己构建（不需要 Android Studio）
需要 JDK 17。脚本里 SDK 路径默认是
`~/workspace/android-sdk`（platform-33 + build-tools 34），
改 `build.sh` 顶部路径后执行：

```bash
bash build.sh
```

产物就是项目根目录下的 `BtCodecViewer.apk`。
