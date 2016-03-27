package gopack;

import java.io.Serializable;

public class goPacket implements Serializable{

    private static final long serialVersionUID = 1L;
    public int sequencenumber;
    public int checksum;
    public short type;
    public byte data[];
    public long sentTime;

    public goPacket(int sequencenumber, int checksum, short type, byte[] data) {
        this.sequencenumber = sequencenumber;
        this.checksum = checksum;
        this.type = type;
        this.data = data;
    }

    public boolean equals(Object o){
        if(o == null || !(o instanceof  goPacket)) return false;
        goPacket that = (goPacket)o;
        return this.sequencenumber == that.sequencenumber;
    }

    public int hashCode(){
        return new Integer(this.sequencenumber).hashCode();
    }
}

