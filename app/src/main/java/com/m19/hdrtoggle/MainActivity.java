package com.m19.hdrtoggle;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.cgutman.adblib.AdbBase64;
import com.cgutman.adblib.AdbConnection;
import com.cgutman.adblib.AdbCrypto;
import com.cgutman.adblib.AdbStream;

import java.io.File;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private TextView tvStatus;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        Button btnEnableSdr = findViewById(R.id.btn_enable_sdr);
        Button btnRestoreHdr = findViewById(R.id.btn_restore_hdr);

        btnEnableSdr.setOnClickListener(v -> executeShellCommand(
            "setprop persist.sys.hdr.policy 2\n" +
            "setprop persist.sys.hdr.mode 0\n" +
            "setprop persist.sys.sdr2hdr 0\n" +
            "settings put global hdr_mode 0\n" +
            "settings put system hdr_policy 2\n",
            "已强制开启 HDR to SDR"
        ));

        btnRestoreHdr.setOnClickListener(v -> executeShellCommand(
            "setprop persist.sys.hdr.policy 0\n" +
            "setprop persist.sys.hdr.mode 1\n" +
            "settings put global hdr_mode 1\n",
            "已恢复默认设置"
        ));
    }

    private void executeShellCommand(String cmd, String successMsg) {
        tvStatus.setText("正在执行命令...");
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 连接本机的 5555 端口
                Socket socket = new Socket("127.0.0.1", 5555);
                
                // 本地快速生成/复用 ADB 证书
                File privKey = new File(getFilesDir(), "priv.key");
                File pubKey = new File(getFilesDir(), "pub.key");
                AdbCrypto crypto;
                AdbBase64 base64 = android.util.Base64::encodeToString;
                if (!privKey.exists()) {
                    crypto = AdbCrypto.generateAdbKeyPair(base64);
                    crypto.saveAdbKeyPair(privKey, pubKey);
                } else {
                    crypto = AdbCrypto.loadAdbKeyPair(base64, privKey, pubKey);
                }

                AdbConnection adb = AdbConnection.create(socket, crypto);
                adb.connect();

                // 执行 Shell 命令并断开
                AdbStream stream = adb.open("shell:" + cmd);
                stream.close();
                adb.close();

                mainHandler.post(() -> {
                    tvStatus.setText("当前状态：" + successMsg);
                    Toast.makeText(MainActivity.this, successMsg, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvStatus.setText("执行失败: 请确保网络ADB 5555已开启");
                    Toast.makeText(MainActivity.this, "连接失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
