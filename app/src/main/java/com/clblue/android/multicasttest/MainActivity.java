package com.clblue.android.multicasttest;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * 组播搜索测试
 *
 * Created by clblue in 2017/04/05 21:53.
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int PRINT_LOG = 0x1;

    private static final String LOG = "log";

    private EditText et_send_ip,et_send_port,et_receive_ip,et_receive_port;

    private Button btn_start,btn_stop,btn_clean;

    /**
     * 日志
     */
    private TextView tv_log;

    /**
     * 是否正在搜索
     */
    private boolean isReveiving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    /**
     * 初始化控件
     */
    private void init() {
        et_send_ip = (EditText) findViewById(R.id.et_send_ip);
        et_send_port = (EditText) findViewById(R.id.et_send_port);
        et_receive_ip = (EditText) findViewById(R.id.et_receive_ip);
        et_receive_port = (EditText) findViewById(R.id.et_receive_port);
        tv_log = (TextView) findViewById(R.id.tv_log);

        btn_start = (Button) findViewById(R.id.btn_start);
        btn_stop = (Button) findViewById(R.id.btn_stop);
        btn_clean = (Button) findViewById(R.id.btn_clean);

        btn_start.setOnClickListener(this);
        btn_stop.setOnClickListener(this);
        btn_clean.setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start:
                startSearch();
                break;
            case R.id.btn_stop:
                stopSearch();
                break;
            case R.id.btn_clean:
                cleanLog();
                break;
        }
    }

    /**
     * 清屏
     */
    private void cleanLog() {
        tv_log.setText("");
    }

    /**
     * 停止搜索
     */
    private void stopSearch() {
        if(multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        isReveiving = false;
        printLog("停止搜索，释放组播锁");
    }

    /**
     * 开始搜索
     */
    private void startSearch() {
        if (isReveiving) {
            printLog("已在搜索，请勿重复操作！isReceiving = true");
        }
        isReveiving = true;
        allowMulticast();
        startReceive();
        startSend();
    }

    WifiManager wifiManager;
    WifiManager.MulticastLock multicastLock;

    /**
     * 获取组播锁
     */
    private void allowMulticast() {
        printLog("开始配置组播锁");
        wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        multicastLock = wifiManager.createMulticastLock("multicast.test");
        multicastLock.acquire();
        printLog("组播锁开启");
        if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            printLog("wifi信息如下："+wifiInfo.toString());
        }
        else{
            printLog("wifi state is disable");
        }
    }

    /**
     * 开始发送
     */
    private void startSend() {
        printLog("开启发送线程");
        final String sendIp = et_send_ip.getText().toString();
        final int sendPort = Integer.parseInt(et_send_port.getText().toString());
        //TODO:正则表达式验证IP

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    MulticastSocket multicastSocket = new MulticastSocket(sendPort);
                    multicastSocket.setLoopbackMode(true);
                    InetAddress group = InetAddress.getByName(sendIp);
                    multicastSocket.joinGroup(group);
                    byte[] data = "send multicast from phone".getBytes();
                    DatagramPacket packet = new DatagramPacket(data, data.length, group, sendPort);
                    sendToMain("发送开始：发送端口为" + sendPort + ",发送组播地址为：" + sendIp);
                    int count = 0;
                    while (isReveiving) {
                        //每隔一秒发送一次
                        multicastSocket.send(packet);
                        ++count;
                        sendToMain("发送组播：count = " + count);
                        Thread.sleep(2000);
                    }
                    multicastSocket.leaveGroup(group);
                    multicastSocket.close();
                    sendToMain("发送结束，离开组播组");
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 开始接收
     */
    private void startReceive() {
        printLog("开启接收线程");
        final String recvIp = et_receive_ip.getText().toString();
        final int recvPort = Integer.parseInt(et_receive_port.getText().toString());
        //TODO:正则表达式验证IP

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    MulticastSocket multicastSocket = new MulticastSocket(recvPort);
                    multicastSocket.setLoopbackMode(true);
                    InetAddress group = InetAddress.getByName(recvIp);
                    multicastSocket.joinGroup(group);
                    byte[] data = new byte[64];
                    DatagramPacket packet = new DatagramPacket(data, data.length);
                    sendToMain("接收开始：接收端口为" + recvPort + ",接收组播地址为：" + recvIp);
                    int count = 0;
                    while (isReveiving) {
                        multicastSocket.receive(packet);

                        //获取接收到的报文的发送地址
                        InetAddress address = packet.getAddress();
                        String ip = address.getHostAddress();
                        ++count;
                        ip = "接收到的ip地址为：" + ip + ",count =" + count;
                        sendToMain(ip);
                    }
                    multicastSocket.leaveGroup(group);
                    multicastSocket.close();
                    sendToMain("接收结束，离开组播组");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 发送到主线程，显示并打印日志
     */
    private void sendToMain(String ip) {
        Bundle bundle = new Bundle();
        bundle.putString(LOG, ip);
        Message message = new Message();
        message.what = PRINT_LOG;
        message.setData(bundle);
        handler.sendMessage(message);
    }

    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {

                case PRINT_LOG:
                    Bundle bundle = msg.getData();
                    String receivedIP = bundle.getString(LOG, "received null");
                    printLog(receivedIP);
                    break;
            }
            return true;
        }
    });

    /**
     * 将字符串拼接得到tv_log中，并打印log
     */
    private void printLog(String s) {
        //显示到TextView中
        String log = tv_log.getText().toString() + "\n"+s;
        tv_log.setText(log);
        //打印日志
        Log.e(TAG, "printLog: " + s);
    }

}
