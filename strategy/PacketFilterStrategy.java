package strategy;
import firewall.NetworkPacket;

public interface PacketFilterStrategy {
    public abstract boolean analyzePacket(NetworkPacket packet);
}