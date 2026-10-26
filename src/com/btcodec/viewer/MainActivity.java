package com.btcodec.viewer;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class MainActivity extends Activity {

    private BluetoothAdapter adapter;
    private BluetoothProfile a2dp;
    private TextView tvInfo;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean auto = false;
    private static final int REQ_BT = 1001;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            if (auto) handler.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        tvInfo = findViewById(R.id.tvInfo);
        Button btn = findViewById(R.id.btnRefresh);
        Button btnAuto = findViewById(R.id.btnAuto);
        btn.setOnClickListener(v -> refresh());
        btnAuto.setOnClickListener(v -> {
            auto = !auto;
            ((Button) v).setText("自动刷新:" + (auto ? "开" : "关"));
            if (auto) handler.post(ticker);
        });

        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
        }

        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm != null) adapter = bm.getAdapter();
        if (adapter == null) {
            tvInfo.setText("这台设备没有蓝牙。");
            return;
        }
        if (!adapter.isEnabled()) {
            tvInfo.setText("蓝牙没打开，先打开蓝牙并连上耳机，再回来刷新。");
        }
        bindProfile();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT && a2dp == null) bindProfile();
    }

    private void bindProfile() {
        if (adapter == null) return;
        // A2DP profile = 2
        adapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                a2dp = proxy;
                refresh();
            }
            @Override public void onServiceDisconnected(int profile) {
                a2dp = null;
            }
        }, BluetoothProfile.A2DP);
    }

    private void refresh() {
        StringBuilder sb = new StringBuilder();
        if (a2dp == null) {
            sb.append("A2DP 服务还没连上，稍后自动重试…\n");
            setText(sb);
            return;
        }
        try {
            List<BluetoothDevice> devices = a2dp.getConnectedDevices();
            if (devices == null || devices.isEmpty()) {
                sb.append("没有已连接的 A2DP 设备。\n请先在系统里连上蓝牙耳机/音箱。");
                setText(sb);
                return;
            }
            BluetoothDevice dev = devices.get(0);
            sb.append("设备: ").append(dev.getName())
              .append("\nMAC : ").append(dev.getAddress());

            Object status = call(a2dp, "getCodecStatus",
                    new Class[]{BluetoothDevice.class}, dev);
            if (status == null) {
                sb.append("\n\n系统拒绝返回编码状态（部分 ROM 会限制）。");
                setText(sb);
                return;
            }
            Object cfg = call(status, "getCodecConfig", null);
            if (cfg == null) {
                sb.append("\n\n读不到当前编码配置。");
                setText(sb);
                return;
            }

            int codec = asInt(read(cfg, "getCodecType", "mCodecType"));
            int rate = asInt(read(cfg, "getSampleRate", "mSampleRate"));
            int bits = asInt(read(cfg, "getBitsPerSample", "mBitsPerSample"));
            int ch = asInt(read(cfg, "getChannelMode", "mChannelMode"));
            int s1 = asInt(read(cfg, "getCodecSpecific1", "mCodecSpecific1"));
            int s2 = asInt(read(cfg, "getCodecSpecific2", "mCodecSpecific2"));
            int s3 = asInt(read(cfg, "getCodecSpecific3", "mCodecSpecific3"));
            int s4 = asInt(read(cfg, "getCodecSpecific4", "mCodecSpecific4"));

            sb.append("\n\n===== 当前协商参数 =====\n");
            sb.append("编码 : ").append(codecName(codec)).append("  (原始值 ").append(codec).append(")");
            sb.append("\n采样率: ").append(rateName(rate));
            sb.append("\n位深 : ").append(bitsName(bits));
            sb.append("\n声道 : ").append(chName(ch));
            sb.append("\n\n===== 码率档位（标称）=====\n");
            sb.append(bitrateText(codec, s1));
            sb.append("\n\n===== 厂商原始字段 =====\n");
            sb.append("specific1 = ").append(s1);
            sb.append("\nspecific2 = ").append(s2);
            sb.append("\nspecific3 = ").append(s3);
            sb.append("\nspecific4 = ").append(s4);
            sb.append("\n\n说明: 上面显示的是系统【协商好的档位】。\n");
            sb.append("Android 不开放逐秒动态 kbps，\n");
            sb.append("LHDC/LDAC 遇到干扰会自动降速(如 900→600→400)，\n");
            sb.append("非 root 手机任何 App 都读不到实时数字，\n");
            sb.append("要看真实曲线只能用电脑抓 HCI 日志。");
        } catch (Exception e) {
            sb.append("\n\n读取失败: ").append(e);
        }
        setText(sb);
    }

    private void setText(StringBuilder sb) {
        tvInfo.post(() -> tvInfo.setText(sb.toString()));
    }

    /* ---------- 反射工具 ---------- */

    private static Object call(Object obj, String method, Class<?>[] types, Object... args) {
        try {
            Method m = obj.getClass().getMethod(method, types);
            m.setAccessible(true);
            return m.invoke(obj, args);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object read(Object obj, String getter, String field) {
        try {
            Method m = obj.getClass().getMethod(getter);
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Exception ignored) { }
        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static int asInt(Object o) {
        return (o instanceof Number) ? ((Number) o).intValue() : -1;
    }

    /* ---------- 常量映射 ---------- */

    private static String codecName(int t) {
        switch (t) {
            case 0: return "SBC";
            case 1: return "AAC";
            case 2: return "aptX";
            case 3: return "aptX HD";
            case 4: return "LDAC";
            case 5: return "LC3";
            case 6: return "LC3plus";
            case 7: return "Opus";
            case 8: return "LHDC V2(推测)";
            case 9: return "LHDC V3(推测)";
            case 10: return "LHDC V5(推测)";
            default: return "厂商自定义 0x" + Integer.toHexString(t);
        }
    }

    private static String rateName(int v) {
        if (v >= 4000) return v + " Hz（直接值）";
        switch (v) {
            case 2: return "44100 Hz";
            case 4: return "48000 Hz";
            case 8: return "88200 Hz";
            case 16: return "96000 Hz";
            case 32: return "176400 Hz";
            case 64: return "192000 Hz";
            default: return "未知(0x" + Integer.toHexString(v) + ")";
        }
    }

    private static String bitsName(int v) {
        switch (v) {
            case 16: case 2: return "16 bit";
            case 24: case 4: return "24 bit";
            case 32: case 8: return "32 bit";
            default: return "未知(0x" + Integer.toHexString(v) + ")";
        }
    }

    private static String chName(int v) {
        switch (v) {
            case 2: return "单声道";
            case 4: return "立体声";
            default: return "未知(0x" + Integer.toHexString(v) + ")";
        }
    }

    private static String bitrateText(int codec, int s1) {
        switch (codec) {
            case 0: return "SBC: 固定 ≤ 328 kbps";
            case 1: return "AAC: 约 250~320 kbps（看编码器）";
            case 2: return "aptX: 固定 352 kbps";
            case 3: return "aptX HD: 固定 576 kbps";
            case 4:
                switch (s1) {
                    case 1000: return "LDAC 音质优先: 990 kbps";
                    case 1001: return "LDAC 标准: 660 kbps";
                    case 1002: return "LDAC 兼顾连接: 330 kbps";
                    case 1003: return "LDAC 自适应(330~990)";
                    default: return "LDAC(档位字段=" + s1 + ") 范围 330~990 kbps";
                }
            case 8: case 9: case 10:
                return "LHDC: 最高约 900~1000 kbps\n"
                     + "请在欢律/蓝牙设置选「音质优先」\n"
                     + "档位原始值 specific1=" + s1 + "\n"
                     + "(LHDC 档位定义各厂商不公开)";
            case 5: return "LC3: 码率可变，看具体场景";
            case 6: return "LC3plus: 码率可变，最高约 960~1920 kbps";
            case 7: return "Opus: 码率可变";
            default: return "厂商编码，系统未公开码率档位";
        }
    }
}
