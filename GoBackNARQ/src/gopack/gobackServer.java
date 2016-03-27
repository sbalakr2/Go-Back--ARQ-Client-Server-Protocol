package gopack;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;


public class gobackServer extends Thread {
    
    private int port;
    private String filename;
    private double probability;
    private int acknowledged = -1;
    private volatile boolean isReceive;
    private DatagramSocket datagramSocket = null;

    public gobackServer(int port, String filename, double probability) throws SocketException {
        this.port = port;
        this.filename = filename;
        this.probability = probability;
        datagramSocket = new DatagramSocket(this.port);
        isReceive = true;
        System.out.println("The probability is"+ probability);
    }

    public void run() {
        BufferedOutputStream bos = null;
        try {
        	int lostPacketCnt = 0;
            System.out.println("Server running at " + InetAddress.getLocalHost().getHostAddress()
            		+ " on port " + datagramSocket.getLocalPort());
            File file = new File(filename);
        	FileOutputStream fostream = new FileOutputStream(file);
            bos = new BufferedOutputStream(fostream);
            lostPacketCnt = getDataUsingGoback(bos, lostPacketCnt);
        } catch (ClassNotFoundException | IOException e) {
        	System.out.println("Error");
            isReceive = false;
        }
    }
    

	private int getDataUsingGoback(BufferedOutputStream bos,
			int lostPacketCnt) throws IOException, ClassNotFoundException {
		int typeConst = 21845;
		while (isReceive) {
		    byte databuffer[] = new byte[1024 * 1000 * 2];
		    int bufferLen = databuffer.length;
		    DatagramPacket datagrampacket = new DatagramPacket(databuffer, bufferLen);
		    datagramSocket.receive(datagrampacket);
		    ByteArrayInputStream baostream = new ByteArrayInputStream(datagrampacket.getData());
		    ObjectInputStream outputStream = new ObjectInputStream(baostream);
		    goPacket gopacket = (goPacket) outputStream.readObject();
		    if(gopacket == null){
		        bos.close();
		        System.out.println("Packets Lost = " + lostPacketCnt);
		        datagramSocket.close();
		        break;
		    }

		    Random random = new Random();
		    int randomNum = random.nextInt(100);
		    double randomProbability = (double)randomNum/100;
		    int checkSum = checksum(gopacket.data);
		    int seqNum = gopacket.sequencenumber;
		    
		    if(seqNum != acknowledged) {
		    	lostPacketCnt = calProbability(bos, lostPacketCnt,
						typeConst, datagrampacket, gopacket, randomProbability,
						checkSum, seqNum);
		    } else {
		    	sendAcknowledgement(seqNum, datagrampacket);
		    }
		}
		return lostPacketCnt;
	}
	

	private int calProbability(BufferedOutputStream bos,
			int lostPacketCnt, int typeConst, DatagramPacket datagrampacket,
			goPacket gopacket, double randomProbability, int checkSum,
			int seqNum) throws IOException {
			if(probability < randomProbability) {
				if (gopacket.type == typeConst) {			
					if(gopacket.checksum == checkSum) {
						acknowledged++;
						bos.write(gopacket.data);
						bos.flush();
						sendAcknowledgement(seqNum, datagrampacket);
			         }
					else {
						System.out.printf("Checksum for sequence number: %d is incorrect\n", seqNum);
					}
				}
			}
			else {
				System.out.printf("Packet loss, sequence number = %d \n", seqNum);
				lostPacketCnt++;
			}
		return lostPacketCnt;
	}

   
    private void sendAcknowledgement(int seqNum, DatagramPacket datagrampacket) throws IOException {
    	byte ackData[] = null;
    	short chkSum = 0;
        short ackType = (short)43690;
        goPacket acknowledgement = new goPacket(seqNum, chkSum, ackType, ackData);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream outStream = new ObjectOutputStream(baos);
        outStream.writeObject(acknowledgement);
        byte[] acknowledgeData = baos.toByteArray();
        int acknDataLen = acknowledgeData.length;
        DatagramPacket ackPacket = new DatagramPacket(acknowledgeData, acknDataLen,
                                   datagrampacket.getAddress(), datagrampacket.getPort());
        datagramSocket.send(ackPacket);
    }

    private static int checksum(byte data[]) {
        int checkSumValue = 0;
        if (data == null) return 0;
        try {
        	for(int i = 0; i < data.length; i++) {
            	checkSumValue = ((i % 2) == 0) ? (checkSumValue + ((data[i] << 8) & 0xFF00)) 
            			: (checkSumValue + ((data[i]) & 0xFF));

	        	if((data.length % 2) != 0){
	        		checkSumValue = checkSumValue + 0xFF;
	        	}
	
	            while ((checkSumValue >> 16) == 1){
	            	 checkSumValue =  ((checkSumValue & 0xFFFF) + (checkSumValue >> 16));
	                 checkSumValue =  ~checkSumValue;
	            }
        	} 
        }
        catch (Exception e) {}
        return checkSumValue;
    }
    
    public static void main(String[] args) throws SocketException {
        if (args.length != 3) {
            System.out.println("Run as: java Server port# file-name p");
        }
        int port = Integer.parseInt(args[0]);
        String filename = args[1];
        double probability = Double.parseDouble(args[2]);
        new gobackServer(port, filename, probability).start();
    }

}
