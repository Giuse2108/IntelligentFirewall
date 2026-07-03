package strategy;
import firewall.NetworkPacket;

/**
 * Strategia STATICA: blocca il pacchetto se almeno uno di questi valori
 * supera la soglia fissa (stessa logica originale del progetto).
 *   duration  >= 1000 ms  → anomalia
 *   src_bytes >= 1000     → anomalia
 *   dst_bytes >= 1000     → anomalia
 *
 * Ritorna true  = pacchetto SICURO
 *          false = ANOMALIA
 */
public class SimpleFilter implements PacketFilterStrategy {

    @Override
    public boolean analyzePacket(NetworkPacket packet) {
        return packet.getDuration() < 1000
            && packet.getSrc_bytes() < 1000
            && packet.getDst_bytes() < 1000;
    }
}