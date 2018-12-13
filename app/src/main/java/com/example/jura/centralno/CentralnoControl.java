package com.example.jura.centralno;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;

import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


public class CentralnoControl extends AppCompatActivity {

    Button btnIzvjesce, btntempplus, bnttempmin, btngreske, btnDis;
    TextView prikaz;
    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;
    //SPP UUID. Look for it
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    public void onBackPressed() {
        disconnect();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent newint = getIntent();
        address = newint.getStringExtra(com.example.jura.centralno.DeviceList.EXTRA_ADDRESS); //receive the address of the bluetooth device

        //view of the ledControl
        setContentView(R.layout.activity_led_control);

        //call the widgtes
        btnIzvjesce = (Button) findViewById(R.id.button2);
        btntempplus = (Button) findViewById(R.id.button4);
        bnttempmin = (Button) findViewById(R.id.button3);
        btngreske = (Button) findViewById(R.id.button5);
        btnDis = (Button) findViewById(R.id.button6);
        prikaz = (TextView) findViewById(R.id.prikaz_view);

        new ConnectBT().execute(); //Call the class to connect

        //commands to be sent to bluetooth
        btnIzvjesce.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                posalji(12);      //method to turn on
            }
        });

        btntempplus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                posalji(10);   //method to turn off
            }
        });

        bnttempmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                posalji(11);   //method to turn off
            }
        });

        btngreske.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                posalji(13);   //method to turn off
            }
        });

        btnDis.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect(); //close connection
            }
        });

    }

    private void disconnect() {
        if (btSocket != null) //If the btSocket is busy
        {
            try {
                stopWorker = true;
                mmOutputStream.close();
                mmInputStream.close();
                btSocket.close(); //close connection
            } catch (IOException e) {
                msg("Error");
            }
        }
        finish(); //return to the first layout

    }

    private void posalji(int byteToSend) {
        if (btSocket != null) {
            try {
                mmOutputStream.write(byteToSend);
                beginListenForData();
            } catch (IOException e) {
                msg("Error");
            }
        }
    }

    void beginListenForData() {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character
        prikaz.setText("");
        stopWorker = false;
        readBufferPosition = 10;
        readBuffer = new byte[2048];
        workerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                final byte b = packetBytes[i];
                                if (b == delimiter) {
                                    readBuffer[readBufferPosition++] = b; // Adds delimiter on end!
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable() {
                                        public void run() {
                                            String tmp = prikaz.getText().toString();
                                            prikaz.setText(tmp + data);
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }


    // fast way to call Toast
    private void msg(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }


    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute() {
            progress = ProgressDialog.show(CentralnoControl.this, "Spajam se...", "Pričekaj!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try {
                if (btSocket == null || !isBtConnected) {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                    mmInputStream = btSocket.getInputStream();
                    mmOutputStream = btSocket.getOutputStream();
                }
            } catch (IOException e) {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess) {
                msg("Nemogu se spojiti, probajte ponovo!");
                finish();
            } else {
                msg("Spojen!");
                isBtConnected = true;
            }
            progress.dismiss();
        }
    }
}
