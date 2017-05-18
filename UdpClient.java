    

import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.*;

public class UdpClient{

	public static void main(String[] args)throws Exception{
		try(Socket socket = new Socket("codebank.xyz", 38005)){
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			
			out.write(handshake(socket));
			byte[] code = new byte[4];
			
			in.read(code);
			System.out.print("Handshake:\n0x");
			
			for(byte e: code)
				System.out.printf("%02X", e);
			System.out.println("\n");
			
			byte[] destPort = new byte[2];
			in.read(destPort);
			
			for(int i = 1; i < 13; i++){
				int udpDataSize = (int)Math.pow(2.0, (double)i);
				byte[] packet = buildPacket(destPort, udpDataSize, socket);
				long start = System.currentTimeMillis();
				out.write(packet);
				long end = System.currentTimeMillis();
				System.out.print("packet size: " + udpDataSize + "\n0x");
				
				for(byte e: code)
					System.out.printf("%02X", e);
				System.out.println("\nRTT: " + (end-start) + " ms\n");
			}
		}
	}

	private static byte[] handshake(Socket socket){
		byte[] destArray = socket.getInetAddress().getAddress();
		byte[] packet = new byte[24];
		packet[0] = 0b01000101; //version 4 and HLen 5
		packet[1] = 0; //TOS
		packet[2] = 0;  //first byte of length
		packet[3] = 24; //second byte of length
		packet[4] = 0; //first byte of Ident
		packet[5] = 0; //second byte of Ident
		packet[6] = (byte) 0x40; //flags and offset
		packet[7] = 0; //offset cont'd
		packet[8] = 50; //TTL
		packet[9] = 17; //protocol: UDP
		packet[10] = 0; //assume checksum 0 first
		packet[11] = 0; //assume checksum 0 first
		for(int j = 12; j < 16; j++) //all 0s for sourceAddr
		packet[j] = 0;
		for(int i = 0; i < 4; i++)
			packet[i+16] = destArray[i];
		short checksum = checksum(packet, 20); //calc checksum
		packet[10] = (byte)(checksum >>> 8); //first byte of checksum
		packet[11] = (byte)checksum; //second byte of checksum
		packet[20] = (byte) 0xDE; // code:0xDEADBEEF
		packet[21] = (byte) 0xAD;
		packet[22] = (byte) 0xBE;
		packet[23] = (byte) 0xEF;
		return packet;
	}

	private static byte[] buildPacket(byte[] destPort, int udpDataSize, Socket socket) throws Exception{
		byte[] destArray = socket.getInetAddress().getAddress();
		int totalSize = 28 + udpDataSize;
		byte[] packet = new byte[totalSize];
		new Random().nextBytes(packet); //fill random data first
		//IPv4 header
		packet[0] = 0b01000101; //version 4 and HLen 5
		packet[1] = 0; //TOS
		packet[2] = (byte) (totalSize >> 8);  //first byte of length
		packet[3] = (byte) totalSize; //second byte of length
		packet[4] = 0; //first byte of Ident
		packet[5] = 0; //second byte of Ident
		packet[6] = (byte)0x40; //flags and offset
		packet[7] = 0; //offset cont'd
		packet[8] = 50; //TTL
		packet[9] = 17; //protocol: UDP
		packet[10] = 0; //assume checksum 0 first
		packet[11] = 0; //assume checksum 0 first
		for(int j = 12; j < 16; j++) //all 0s for sourceAddr
			packet[j] = 0;
		for(int i = 0; i < 4; i++)
			packet[i+16] = destArray[i];
		short checksum = checksum(packet, 20); //calc checksum
		packet[10] = (byte)(checksum >>> 8); //first byte of checksum
		packet[11] = (byte)checksum; //second byte of checksum
		//UDP header
		//allow SrcPort to be randomised: skipping packet[20] and packet[21]
		packet[22] = destPort[0];
		packet[23] = destPort[1];
		int udpSize = 8 + udpDataSize;
		packet[24] = (byte)(udpSize>>>8); //UDP length
		packet[25] = (byte) udpSize; //UDP length cont'd
		packet[26] = 0; //assume checksum 0 first
		packet[27] = 0; //assume checksum 0 first
		byte[] pseudoHeaderAndUDP = makePsdHdrUDP(packet, udpSize + 12,socket);
		checksum = checksum(pseudoHeaderAndUDP, udpSize + 12);
		packet[26] = (byte)(checksum>>>8); //assume checksum 0 first
		packet[27] = (byte) checksum;
		return packet;
	}

	private static byte[] makePsdHdrUDP(byte[] packet, int size, Socket socket) throws Exception{
		byte[] destArray = socket.getInetAddress().getAddress();
		byte[] psdoHdrUDP = new byte[size];
		psdoHdrUDP[0] = 0; //protocol pad
		psdoHdrUDP[1] = 17; //protocol
		for(int i = 0; i < 4; i++)
			packet[i+6] = destArray[i];
		psdoHdrUDP[10] = packet[24];
		psdoHdrUDP[11] = packet[25];
		for(int i = 12; i < size; i++){
			psdoHdrUDP[i] = packet[i+8];
		}
		return psdoHdrUDP;
	}

	private static short checksum(byte[] packet, int bound){
		long sum = 0;
		for(int i = 0; i < bound; i+=2){
			int thisInt = packet[i] & 0xFF;
			thisInt <<= 8;
			thisInt |= packet[i+1] & 0xFF;
			sum += thisInt;
			if((sum & 0xFFFF0000)!=0){
				sum &= 0x0000FFFF;
				sum++;
			}
		}
		return (short)~sum;
	}

}
