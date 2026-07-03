package firewall;

public class NetworkPacket {
    private int duration, src_bytes, dst_bytes, count, srv_count, dst_host_count, dst_host_srv_count;
    private double serror_rate, rerror_rate, same_srv_rate, diff_srv_rate;
    private String description; // Utile per stampare a video il tipo di test

    public NetworkPacket(String description, int duration, int src_bytes, int dst_bytes, int count,
                         int srv_count, double serror_rate, double rerror_rate, 
                         double same_srv_rate, double diff_srv_rate, 
                         int dst_host_count, int dst_host_srv_count) {
        this.description = description;
        this.duration = duration;
        this.src_bytes = src_bytes;
        this.dst_bytes = dst_bytes;
        this.count = count;
        this.srv_count = srv_count;
        this.serror_rate = serror_rate;
        this.rerror_rate = rerror_rate;
        this.same_srv_rate = same_srv_rate;
        this.diff_srv_rate = diff_srv_rate;
        this.dst_host_count = dst_host_count;
        this.dst_host_srv_count = dst_host_srv_count;
    }

    public int getDuration() { return duration; }
    public int getSrc_bytes() { return src_bytes; }
    public int getDst_bytes() { return dst_bytes; }
    public int getCount() { return count; }
    public int getSrv_count() { return srv_count; }
    public double getSerror_rate() { return serror_rate; }
    public double getRerror_rate() { return rerror_rate; }
    public double getSame_srv_rate() { return same_srv_rate; }
    public double getDiff_srv_rate() { return diff_srv_rate; }
    public int getDst_host_count() { return dst_host_count; }
    public int getDst_host_srv_count() { return dst_host_srv_count; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return String.format("[%s] -> Bytes(src:%d, dst:%d), Connessioni:%d, Tasso_Errore:%.2f", 
                description.toUpperCase(), src_bytes, dst_bytes, count, serror_rate);
    }
}