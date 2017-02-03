package com.example.dima.blueardu;


import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import static android.R.layout.*;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;

    private BluetoothAdapter bluetoothAdapter;

    private ArrayList<String> pairedDeviceArrayList;
    private TextView mTvConnectMessage;
    private ListView listViewPairedDevice;
    private FrameLayout ButPanel;

    private ArrayAdapter<String> pairedDeviceAdapter;
    private UUID myUUID;

    private IntentFilter intentFilter;
    private ThreadConnectBTdevice myThreadConnectBTdevice;
    private ThreadConnected myThreadConnected;
    private BroadcastReceiver mReconnector;
    private Handler reconnectHandler;
    private Runnable reconnectRunnable;
    private ProgressDialog progressDialog;
    private StringBuilder sb = new StringBuilder();
    // private static final String CLOSE_CONNECT_ACTION = "com.example.dima.blueardu.MainActivity.CLOSE_BLUETOOTH_CONNESTION";

    public TextView textInfo, d10, d11, d12, d13;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final String UUID_STRING_WELL_KNOWN_SPP = "8ce255c0-200a-11e0-ac64-0800200c9a66";

        textInfo = (TextView)findViewById(R.id.textInfo);
        d10 = (TextView)findViewById(R.id.d10);
        d11 = (TextView)findViewById(R.id.d11);
        d12 = (TextView)findViewById(R.id.d12);
        d13 = (TextView)findViewById(R.id.d13);
        mTvConnectMessage = (TextView) findViewById(R.id.tv_connect_message);
        listViewPairedDevice = (ListView)findViewById(R.id.pairedlist);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Reconnect");
        progressDialog.setCancelable(false);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Отмена", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                stopReconnect();
                if (mReconnector!=null) unregisterReceiver(mReconnector);
                if (listViewPairedDevice.getVisibility() != View.VISIBLE) {
                    listViewPairedDevice.setVisibility(View.VISIBLE);
                    ButPanel.setVisibility(View.GONE);
                }
                mTvConnectMessage.setText("Связь разорвана");
            }
        });

        ButPanel = (FrameLayout) findViewById(R.id.ButPanel);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)){
            Toast.makeText(this, "BLUETOOTH NOT support", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        myUUID = UUID.fromString(UUID_STRING_WELL_KNOWN_SPP);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        initializeReconnectionReceiver();
        intentFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this hardware platform", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String stInfo = bluetoothAdapter.getName() + " " + bluetoothAdapter.getAddress();
        textInfo.setText(String.format("Это устройство: %s", stInfo));

    } // END onCreate


    public void initializeReconnectionReceiver() {
        mReconnector = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("Logos", "onReceive " + intent.getAction());
                String action = intent.getAction();
                if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                    if (!progressDialog.isShowing()) {
                        progressDialog.show();
                        startReconnect(intent);
                    }
                }
            }
        };
    }

    public void startReconnect(final Intent intent) {
             if (reconnectHandler != null && reconnectRunnable != null) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
        }

        reconnectHandler = new Handler();
        reconnectRunnable = new Runnable() {
            @Override
            public void run() {
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (myThreadConnected != null && myThreadConnected.isAlive()) {
                        stopReconnect();
                        mTvConnectMessage.setText("Success connected to " + device.getName()
                                + " with MAC " + device.getAddress());
                        return;
                    }
                    myThreadConnectBTdevice = new ThreadConnectBTdevice(device);
                    myThreadConnectBTdevice.start();
                    mTvConnectMessage.setText("Reconnect to " + device.getName() +
                            " with address " + device.getAddress());
                }
                reconnectHandler.postDelayed(reconnectRunnable, 2000);
            }
        };
        reconnectHandler.post(reconnectRunnable);
    }

    public void stopReconnect() {
        if (reconnectHandler != null && reconnectRunnable != null) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
        }
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    @Override
    protected void onStart() { // Запрос на включение Bluetooth
        super.onStart();

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        setup();
    }

    private void setup() { // Создание списка сопряжённых Bluetooth-устройств

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) { // Если есть сопряжённые устройства

            pairedDeviceArrayList = new ArrayList<>();

            for (BluetoothDevice device : pairedDevices) { // Добавляем сопряжённые устройства - Имя + MAC-адресс
                pairedDeviceArrayList.add(device.getName() + "\n" + device.getAddress());
            }

            pairedDeviceAdapter = new ArrayAdapter<>(this, simple_list_item_1, pairedDeviceArrayList);
            listViewPairedDevice.setAdapter(pairedDeviceAdapter);

            listViewPairedDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() { // Клик по нужному устройству

                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                    listViewPairedDevice.setVisibility(View.GONE); // После клика скрываем список

                    String  itemValue = (String) listViewPairedDevice.getItemAtPosition(position);
                    String MAC = itemValue.substring(itemValue.length() - 17); // Вычленяем MAC-адрес

                    BluetoothDevice device2 = bluetoothAdapter.getRemoteDevice(MAC);

                    myThreadConnectBTdevice = new ThreadConnectBTdevice(device2);
                    myThreadConnectBTdevice.start();  // Запускаем поток для подключения Bluetooth

                    if (intentFilter != null && mReconnector != null) {
                        registerReceiver(mReconnector, intentFilter);
                    }
                }
            });
        }
    }

    @Override
    protected void onDestroy() { // Закрытие приложения
        super.onDestroy();
        if (myThreadConnectBTdevice!=null) myThreadConnectBTdevice.cancel();
        if (mReconnector!=null) unregisterReceiver(mReconnector);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT){ // Если разрешили включить Bluetooth, тогда void setup()

            if(resultCode == Activity.RESULT_OK) {
                setup();
            }

            else { // Если не разрешили, тогда закрываем приложение

                Toast.makeText(this, "BlueTooth не включён", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }


    private class ThreadConnectBTdevice extends Thread { // Поток для коннекта с Bluetooth

        private BluetoothSocket bluetoothSocket = null;
        private ThreadConnectBTdevice(BluetoothDevice device) {

            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(myUUID);
            }

            catch (IOException e) {
                e.printStackTrace();
            }
        }


        @Override
        public void run() { // Коннект
            boolean success = false;

            try {
                bluetoothSocket.connect();
                success = true;
            }

            catch (IOException e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Нет коннекта, проверьте Bluetooth-устройство с которым хотите соединиться!",
                                Toast.LENGTH_SHORT).show();
                        listViewPairedDevice.setVisibility(View.VISIBLE);
                    }
                });

                try {
                    bluetoothSocket.close();
                }

                catch (IOException e1) {

                    e1.printStackTrace();
                }
            }

            if (success) {  // Если законнектились, тогда открываем панель с кнопками и запускаем поток приёма и отправки данных

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        ButPanel.setVisibility(View.VISIBLE); // открываем панель с кнопками
                    }
                });

                myThreadConnected = new ThreadConnected(bluetoothSocket);
                myThreadConnected.start(); // запуск потока приёма и отправки данных
            }
        }


        void cancel() {

            Toast.makeText(getApplicationContext(), "Close - BluetoothSocket", Toast.LENGTH_LONG).show();

            try {
                bluetoothSocket.close();
            }

            catch (IOException e) {
                e.printStackTrace();
            }
        }

    } // END ThreadConnectBTdevice:



    private class ThreadConnected extends Thread {    // Поток - приём и отправка данных

        private final InputStream connectedInputStream;
        private final OutputStream connectedOutputStream;

        private String sbprint;

        ThreadConnected(BluetoothSocket socket) {

            InputStream in = null;
            OutputStream out = null;

            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
            }

            catch (IOException e) {
                e.printStackTrace();
            }

            connectedInputStream = in;
            connectedOutputStream = out;
        }


        @Override
        public void run() { // Приём данных

            while (true) {
                try {
                    byte[] buffer = new byte[1];
                    int bytes = connectedInputStream.read(buffer);
                    String strIncom = new String(buffer, 0, bytes);
                    Log.d("Logos", "read " + strIncom);
                    sb.append(strIncom); // собираем символы в строку
                    int endOfLineIndex = sb.indexOf("\r\n"); // определяем конец строки

                    if (endOfLineIndex > 0) {

                        sbprint = sb.substring(0, endOfLineIndex);
                        Log.d("Logos", "read1 " + sbprint);
                        sb.delete(0, sb.length());

                        runOnUiThread(new Runnable() { // Вывод данных

                            @Override
                            public void run() {

                                switch (sbprint) {

                                    case "D10 ON":
                                        d10.setText(sbprint);
                                        break;

                                    case "D10 OFF":
                                        d10.setText(sbprint);
                                        break;

                                    case "D11 ON":
                                        d11.setText(sbprint);
                                        break;

                                    case "D11 OFF":
                                        d11.setText(sbprint);
                                        break;

                                    case "D12 ON":
                                        d12.setText(sbprint);
                                        break;

                                    case "D12 OFF":
                                        d12.setText(sbprint);
                                        break;

                                    case "D13 ON":
                                        d13.setText(sbprint);
                                        break;

                                    case "D13 OFF":
                                        d13.setText(sbprint);
                                        break;

                                    default:
                                        d13.setText(sbprint);
                                        break;
                                }
                            }
                        });
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }


        void write(byte[] buffer) {
            try {
                Log.d("Logos", "read " + buffer[0]);
                connectedOutputStream.write(buffer);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

/////////////////// Нажатие кнопок /////////////////////
/////////////////////////D10////////////////////////////

    public void onClickBut1(View v) {

        if(myThreadConnected!=null) {

            byte[] bytesToSend = "hello".getBytes();
            myThreadConnected.write(bytesToSend );
        }
    }


    public void onClickBut2(View v) {

        if(myThreadConnected!=null) {

            byte[] bytesToSend = "2".getBytes();
            myThreadConnected.write(bytesToSend );
        }
    }

////////////////////////D11////////////////////////////

    public void onClickBut3(View v) {

        if(myThreadConnected!=null) {

            byte[] bytesToSend = "3".getBytes();
            myThreadConnected.write(bytesToSend );
        }
    }


    public void onClickBut4(View v) {

        if(myThreadConnected!=null) {

            byte[] bytesToSend = "4".getBytes();
            myThreadConnected.write(bytesToSend );
        }
    }

//////////////////////D12//////////////////////////

    public void onClickBut5(View v) {

        if(myThreadConnected!=null) {

            byte[] bytesToSend = "5".getBytes();
            myThreadConnected.write(bytesToSend );
        }
    }


    public void onClickBut6(View v) {

        if(myThreadConnected!=null) {

            byte[] bytesToSend = "6".getBytes();
            myThreadConnected.write(bytesToSend );
        }
    }

    //////////////////////D13//////////////////////////

    public void onClickBut7(View v) {

        if(myThreadConnected!=null) {

            byte[] bytesToSend = "7".getBytes();
            myThreadConnected.write(bytesToSend );
        }
    }


    public void onClickBut8(View v) {

        if(myThreadConnected!=null) {

            byte[] bytesToSend = "8".getBytes();
            myThreadConnected.write(bytesToSend );
        }
    }


} // END

