
package org.mcxa.zephyrlogger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
public class HxmService {
    private static final String TAG = "HrmService";
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    public HxmService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = R.string.HXM_SERVICE_RESTING;
        mHandler = handler;
    }

    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
        mHandler.obtainMessage(R.string.HXM_SERVICE_MSG_STATE, state, -1).sendToTarget();
    }
    public synchronized int getState() {
        return mState;
    }
    public synchronized void start() {
        Log.d(TAG, "start()");
        if (mConnectThread != null) {
            mConnectThread.cancel(); mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel(); mConnectedThread = null;
        }

        setState(R.string.HXM_SERVICE_RESTING);
    }
    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect(): starting connection to " + device);
        if (mState == R.string.HXM_SERVICE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel(); mConnectThread = null;
            }
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(R.string.HXM_SERVICE_CONNECTING);
    }
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "connected() starting ");
        if (mConnectThread != null) {
            mConnectThread.cancel(); mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel(); mConnectedThread = null;
        }
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        Message msg = mHandler.obtainMessage(R.string.HXM_SERVICE_MSG_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(null, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(R.string.HXM_SERVICE_CONNECTED);
        Log.d(TAG, "connected() finished");

    }
    public synchronized void stop() {
        Log.d(TAG, "stop() starting ---- ok, it's a little funny:)");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(R.string.HXM_SERVICE_RESTING);
        Log.d(TAG, "stop() finished");
    }
    private synchronized void reset() {
        Log.d(TAG, "reset() starting");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(R.string.HXM_SERVICE_RESTING);
        Log.d(TAG, "reset() finished");
    }
    private void connectionFailed() {
        Log.d(TAG, "BEGIN connectionFailed");

        setState(R.string.HXM_SERVICE_RESTING);
        Message msg = mHandler.obtainMessage(R.string.HXM_SERVICE_MSG_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(null, "connectionFailed(): Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        Log.d(TAG, "END connectionFailed");

    }
    private void connectionLost() {
        setState(R.string.HXM_SERVICE_RESTING);
        Message msg = mHandler.obtainMessage(R.string.HXM_SERVICE_MSG_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(null, "connectionLost(): Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                Method m = device.getClass().getMethod("createRfcommSocket", new Class[] {int.class});
                tmp = (BluetoothSocket) m.invoke(device, 1);
            } catch (SecurityException e) {
                Log.e(TAG, "ConnectThread() SecurityException");
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "ConnectThread() SecurityException");
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "ConnectThread() SecurityException");
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                Log.e(TAG, "ConnectThread() SecurityException");
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                Log.e(TAG, "ConnectThread() SecurityException");
                e.printStackTrace();
            }

            mmSocket = tmp;
        }

        @Override
        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");
            mAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException e) {
                connectionFailed();
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "ConnectThread.run(): unable to close() socket during connection failure", e2);
                }
                HxmService.this.start();
                return;
            }
            synchronized (HxmService.this) {
                mConnectThread = null;
            }
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel(): close() of connect socket failed", e);
            }
        }
    }
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "ConnectedThread(): starting");

            mmSocket = socket;
            InputStream tmpIn = null;
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread(): temp sockets not created", e);
            }

            mmInStream = tmpIn;

            Log.d(TAG, "ConnectedThread(): finished");

        }
        private final int STX = 0x02;
        private final int MSGID = 0x26;
        private final int DLC = 55;
        private final int ETX = 0x03;

        @Override
        public void run() {
            Log.d(TAG, "ConnectedThread.run(): starting");
            byte[] buffer = new byte[1024];
            int b = 0;
            int bufferIndex = 0;
            int payloadBytesRemaining;
            while (true) {
                try {

                    bufferIndex = 0;
                    while (( b = mmInStream.read()) != STX )
                        ;

                    buffer[bufferIndex++] = (byte) b;

                    if ((b = mmInStream.read()) != MSGID )
                        continue;

                    buffer[bufferIndex++] = (byte) b;
                    if ((b = mmInStream.read()) != DLC )
                        continue;

                    buffer[bufferIndex++] = (byte) b;

                    payloadBytesRemaining = b;

                    while ( (payloadBytesRemaining--) > 0 ) {
                        buffer[bufferIndex++] = (byte) (b = mmInStream.read());
                    }
                    buffer[bufferIndex++] = (byte) (b = mmInStream.read());
                    if ((b = mmInStream.read()) != ETX )
                        continue;

                    buffer[bufferIndex++] = (byte) b;

                    Log.d(TAG, "mConnectedThread: read "+Integer.toString(bufferIndex)+" bytes");
                    mHandler.obtainMessage(R.string.HXM_SERVICE_MSG_READ, bufferIndex, 0, buffer)
                            .sendToTarget();


                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }

            Log.d(TAG, "ConnectedThread.run(): finished");

        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread.cancel(): close() of connect socket failed", e);
            }
        }
    }
}
