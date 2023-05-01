package com.example.ledcontroller;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final String TAG = "MainActivity";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String DEVICE_ADDRESS = "00:00:00:00:00:00"; // adres zewnętrznego urządzenia Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private ConnectedThread connectedThread;
    private Handler handler;

    private TextInputEditText inputTxtLayout;
    private TextInputEditText inputTxt;
    private TextView consoleOut;
    private Button applyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputTxtLayout = findViewById(R.id.inputTxt);
        inputTxt = findViewById(R.id.inputTxt);
        consoleOut = findViewById(R.id.textView);
        applyButton = findViewById(R.id.applyButton);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == ConnectedThread.RESPONSE_MESSAGE) {
                    String response = (String) msg.obj;
                    consoleOut.setText(response);
                }
            }
        };
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = inputTxt.getText().toString();
                if (!text.isEmpty()) {
                    inputTxtLayout.setError(null);
                    sendBluetoothData(text);
                    inputTxt.setText("");
                } else {
                    inputTxtLayout.setError("Pole nie może być puste");
                }
            }
        });

        connectToDevice(device);
    }

    private void connectToDevice(BluetoothDevice device) {
        try {

            // Zapytanie użytkownika o zgodę na dostęp do Bluetooth
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothSocket.connect();
            connectedThread = new ConnectedThread(bluetoothSocket, handler);
            connectedThread.start();
        } catch (IOException e) {
            Log.e(TAG, "Błąd podczas nawiązywania połączenia", e);
            try {
                bluetoothSocket.close();
            } catch (IOException e1) {
                Log.e(TAG, "Błąd podczas zamykania gniazda Bluetooth", e1);
            }
        }
    }

    private void sendBluetoothData(String data) {
        if (connectedThread != null) {
            connectedThread.write(data.getBytes());
        }
    }

    private static class ConnectedThread extends Thread {
        private static final int RESPONSE_MESSAGE = 1;
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final Handler handler;
        private final byte[] buffer;

        public ConnectedThread(BluetoothSocket socket, Handler handler) {
            bluetoothSocket = socket;
            this.handler = handler;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Błąd otwarcia strumieni danych", e);
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
            buffer = new byte[1024];
        }

        @Override
        public void run() {
            int bytes;
            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    String response = new String(buffer, 0, bytes);
                    Message message = handler.obtainMessage(RESPONSE_MESSAGE, response);
                    handler.sendMessage(message);
                } catch (IOException e) {
                    Log.e(TAG, "Błąd odczytu danych z Bluetooth", e);
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Błąd wysyłania danych przez Bluetooth", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Błąd zamknięcia połączenia Bluetooth", e);
            }
        }
    }
}