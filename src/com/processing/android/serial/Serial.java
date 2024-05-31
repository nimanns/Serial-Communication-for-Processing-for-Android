package com.processing.android.serial;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Toast;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.hardware.usb.UsbRequest;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbConstants;
import java.util.HashMap;
import java.nio.ByteBuffer;
import processing.core.PApplet;

/**
 * Serial class to handle USB serial communication on Android devices for Processing for Android.
 * 
 * This library ports its setup and read and write methods from mik3y/usb-serial-for-android: https://github.com/mik3y/usb-serial-for-android
 * 
 * For now it only supports the CDC/ACM protocol.
 */
public class Serial {

    private static final String ACTION_USB_PERMISSION = "com.example.USB_PERMISSION";
    private String readValue = "no data";
    private StringBuilder stringBuilder = new StringBuilder();
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection usbConnection;
    private UsbRequest usbRequest = new UsbRequest();
    private Runnable runnable = new Runnable() {
        public void run() {
            while (true) {
                try {
                    readFromSerial();
                } catch (Exception e) {
                    // Handle exception
                }
            }
        }
    };
    private Thread thread = new Thread(runnable);
    private UsbEndpoint writeEndpoint;
    private UsbEndpoint readEndpoint;
    private UsbEndpoint controlEndpoint;
    private boolean connectionEstablished = false;
    private Context context;
    private static final int SET_LINE_CODING = 0x20;
    private static final int USB_RECIP_INTERFACE = 0x01;
    private final int STOPBITS_1 = 1;
    private final int DATABITS_8 = 8;
    private int baudRate;

    /**
     * Constructor for the Serial class.
     *
     * @param parent    The parent PApplet object.
     * @param baud_rate The baud rate for serial communication.
     */
    public Serial(PApplet parent, int baud_rate) {
        this.context = parent.getActivity();
        this.baudRate = baud_rate;
        this.usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        this.context.registerReceiver(usbReceiver, filter);
        requestPermissionForConnectedDevices();
    }

    /**
     * BroadcastReceiver to handle USB permission requests.
     */
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!ACTION_USB_PERMISSION.equals(action)) {
                return;
            }

            synchronized (this) {
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice == null) {
                    return;
                }

                if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    return;
                }

                UsbInterface usbInterface = usbDevice.getInterface(0);
                usbConnection = usbManager.openDevice(usbDevice);
            if (usbConnection == null) {
                    return;
                }

                if (!usbConnection.claimInterface(usbInterface, true)) {
                    return;
                }

                // Set line coding parameters
                byte[] lineCoding = {
                    (byte) (baudRate & 0xff),
                    (byte) ((baudRate >> 8) & 0xff),
                    (byte) ((baudRate >> 16) & 0xff),
                    (byte) ((baudRate >> 24) & 0xff),
                    STOPBITS_1,
                    0,
                    (byte) DATABITS_8
                };
                int result = usbConnection.controlTransfer(
                    UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE,
                    SET_LINE_CODING, 0, 0, lineCoding, lineCoding.length, 5000
                );
                int value = 0x2 | 0x1;
                usbConnection.controlTransfer(
                    UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE, 0x22, value, 0, null, 0, 5000
                );

                for (int i = 0; i < usbDevice.getInterfaceCount(); ++i) {
                    for (int j = 0; j < usbDevice.getInterface(i).getEndpointCount(); ++j) {
                        UsbEndpoint ep = usbDevice.getInterface(i).getEndpoint(j);
                        if ((ep.getDirection() == UsbConstants.USB_DIR_IN) && 
                            (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT)) {
                            controlEndpoint = ep;
                        } else if ((ep.getDirection() == UsbConstants.USB_DIR_IN) &&
                                   (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                            if (readEndpoint == null) {
                                readEndpoint = ep;
                            }
                        } else if ((ep.getDirection() == UsbConstants.USB_DIR_OUT) &&
                                   (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                        	if (writeEndpoint == null) {
                                writeEndpoint = ep;
                        	}
                        }
                    }
                }

                usbRequest.initialize(usbConnection, readEndpoint);
                if (result >= 0) {
                    connectionEstablished = true;
                    thread.start();
                    Toast.makeText(context, "Connection Established", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    /**
     * Request permissions for connected USB devices.
     */
    private void requestPermissionForConnectedDevices() {
        HashMap<String, UsbDevice> connectedDevices = usbManager.getDeviceList();
        for (UsbDevice usbDevice : connectedDevices.values()) {
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE
            );
            usbManager.requestPermission(usbDevice, pendingIntent);
        }
    }

    /**
     * Check if the USB connection is established.
     *
     * @return True if the connection is established, false otherwise.
     */
    public boolean isConnectionEstablished() {
        return connectionEstablished;
    }

    /**
     * Read data from the serial connection.
     */
    private void readFromSerial() {
        try {
            if (connectionEstablished) {
                byte[] readBuff = new byte[32];
                final ByteBuffer buf = ByteBuffer.wrap(readBuff, 0, 32);
                if (!usbRequest.queue(buf, 32)) {
                    readValue = "ERROR IN USB Q REQ";
                }
                final UsbRequest response = usbConnection.requestWait();
                if (response == null) {
                    readValue = "ERROR IN WAITING FOR USB REQ";
                }
                int nread = buf.position();
                if (nread > 0) {
                    for (int i = 0; i < nread; i++) {
                        char c = (char) readBuff[i];
                        stringBuilder.append(c);
                        if (c == '\n') {
                            readValue = stringBuilder.toString();
                            stringBuilder = new StringBuilder();
                        }
                    }
                } else {
                    readValue = "nread not positive";
                }
            }
        } catch (Exception e) {
            // Handle exception
        }
    }

    /**
     * Read data from the serial connection, returning the result as a String.
     *
     * @return The read data as a String, or an error message.
     */
    public String readFromSerial2() {
        if (connectionEstablished) {
            byte[] readBuff = new byte[32];
            final ByteBuffer buf = ByteBuffer.wrap(readBuff, 0, 32);
            if (!usbRequest.queue(buf, 32)) {
                return "USB REQ FAILED";
            }
            final UsbRequest response = usbConnection.requestWait();
            if (response == null) {
                return "WAITING FAILED";
            }
            int nread = buf.position();
            if (nread > 0) {
                for (int i = 0; i < nread; i++) {
                    char c = (char) readBuff[i];
                    stringBuilder.append(c);
                    if (c == '\n') {
                        String res = stringBuilder.toString();
                        stringBuilder = new StringBuilder();
                        return res;
                    }
                }
            } else {
                return "nread is zero";
            }
        }
        return "No Data";
    }

    /**
     * Get the latest read value from the serial connection.
     *
     * @return The latest read value.
     */
    public String readSerial() {
        return readValue;
    }

    protected byte[] mWriteBuffer;
    protected final Object mWriteBufferLock = new Object();

    /**
     * Get the current monotonic clock time in milliseconds.
     *
     * @return The current monotonic clock time in milliseconds.
     */
    private long monotonickClockMillis() {
        final long NS_PER_MS = 1000000;
        return System.nanoTime() / NS_PER_MS;
    }

    /**
     * Write data to the serial connection.
     *
     * @param src     The data to write.
     * @param length  The length of the data.
     * @param timeout The timeout in milliseconds.
     */
    public void write(final byte[] src, int length, final int timeout) {
        if (!isConnectionEstablished()) return;
        int offset = 0;
        final long endTime = (timeout == 0) ? 0 : (monotonickClockMillis() + timeout);
        length = Math.min(length, src.length);

        if (usbConnection == null) {
            // Handle null connection
        }
        while (offset < length) {
            int requestTimeout;
            final int requestLength;
            final int actualLength;

            synchronized (mWriteBufferLock) {
                final byte[] writeBuffer;

                if (mWriteBuffer == null) {
                    mWriteBuffer = new byte[writeEndpoint.getMaxPacketSize()];
                }
                requestLength = Math.min(length - offset, mWriteBuffer.length);
                if (offset == 0) {
                    writeBuffer = src;
                } else {
                    System.arraycopy(src, offset, mWriteBuffer, 0, requestLength);
                    writeBuffer = mWriteBuffer;
                }
                if (timeout == 0 || offset == 0) {
                    requestTimeout = timeout;
                } else {
                    requestTimeout = (int) (endTime - monotonickClockMillis());
                    if (requestTimeout == 0)
                        requestTimeout = -1;
                }
                if (requestTimeout < 0) {
                    actualLength = -2;
                } else {
                    actualLength = usbConnection.bulkTransfer(writeEndpoint, writeBuffer, requestLength, requestTimeout);
                }
            }
            offset += actualLength;
        }
    }

    /**
     * Write a string to the serial connection.
     *
     * @param data The string data to write.
     */
    public void write(String data) {
        byte[] bytes = data.getBytes();
        int len = bytes.length;
        int timeout = 1000;
        this.write(bytes, len, timeout);
    }
}
