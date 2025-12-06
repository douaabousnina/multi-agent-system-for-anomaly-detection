package agents;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.*;

public class LocalAgent extends Agent {
    
    private String nodeId;
    private double cpuThreshold = 80.0;
    private double bandwidthThreshold = 60.0;
    private int tickCount = 0;
    
    protected void setup() {
        // Récupérer l'ID du nœud
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            nodeId = (String) args[0];
        }
        
        System.out.println("========================================");
        System.out.println("Agent Local " + nodeId + " démarré");
        System.out.println("Agent: " + getLocalName());
        System.out.println("Container: " + here().getName());
        System.out.println("========================================");
        
        // Enregistrer dans le DF (Directory Facilitator)
        registerInDF();
        
        // Ajouter comportement de surveillance
        addBehaviour(new MonitoringBehaviour());
        
        // Ajouter comportement de réception de messages
        addBehaviour(new ReceiveMessagesBehaviour());
    }
    
    private void registerInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("local-monitoring");
        sd.setName("node-" + nodeId);
        dfd.addServices(sd);
        
        try {
            DFService.register(this, dfd);
            System.out.println("✓ Agent enregistré dans le Directory Facilitator");
        } catch (FIPAException e) {
            System.err.println("✗ Erreur lors de l'enregistrement DF: " + e.getMessage());
        }
    }
    
    // Comportement de surveillance périodique
    private class MonitoringBehaviour extends TickerBehaviour {
        
        public MonitoringBehaviour() {
            super(LocalAgent.this, 15000); // Toutes les 15 secondes
        }
        
        protected void onTick() {
            tickCount++;
            
            // Appeler le script Python de monitoring
            MonitoringData data = collectMetrics();
            
            System.out.println("\n[" + nodeId + "] Monitoring #" + tickCount + 
                             " - CPU: " + String.format("%.1f", data.cpuUsage) + "%" +
                             " | Bandwidth: " + String.format("%.1f", data.bandwidth) + "%");
            
            // Vérifier les seuils
            if (data.cpuUsage > cpuThreshold || data.bandwidth > bandwidthThreshold) {
                System.out.println("⚠ SEUIL DÉPASSÉ sur " + nodeId + "!");
                sendAlertToCentralServer(data);
            }
        }
    }
    
    // Comportement de réception de messages
    private class ReceiveMessagesBehaviour extends CyclicBehaviour {
        
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = receive(mt);
            
            if (msg != null) {
                String content = msg.getContent();
                System.out.println("\n[" + nodeId + "] Message reçu: " + content);
                
                // Traiter le message
                handleMessage(msg);
            } else {
                block();
            }
        }
    }
    
    private MonitoringData collectMetrics() {
        // TODO: Appeler le script Python via ProcessBuilder
        // Pour l'instant, valeurs simulées avec variation
        MonitoringData data = new MonitoringData();
        
        // Simuler des pics d'activité périodiques pour déclencher des alertes
        if (tickCount % 4 == 0) {
            // Toutes les 4 itérations, générer des valeurs élevées
            data.cpuUsage = 85.0 + (Math.random() * 10);
            data.bandwidth = 65.0 + (Math.random() * 15);
        } else {
            // Valeurs normales le reste du temps
            data.cpuUsage = 30.0 + (Math.random() * 40);
            data.bandwidth = 20.0 + (Math.random() * 30);
        }
        
        return data;
    }
    
    private void sendAlertToCentralServer(MonitoringData data) {
        // Chercher l'agent serveur central via DF
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("server-service");
        template.addServices(sd);
        
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                ACLMessage alert = new ACLMessage(ACLMessage.INFORM);
                alert.addReceiver(result[0].getName());
                
                String alertContent = String.format("ALERT:%s:CPU=%.2f:BW=%.2f", 
                                                   nodeId, data.cpuUsage, data.bandwidth);
                alert.setContent(alertContent);
                
                send(alert);
                System.out.println("✓ Alerte envoyée au serveur central");
            } else {
                System.err.println("✗ Serveur central non trouvé dans le DF");
            }
        } catch (FIPAException e) {
            System.err.println("✗ Erreur lors de la recherche du serveur: " + e.getMessage());
        }
    }
    
    private void handleMessage(ACLMessage msg) {
        // Traiter les commandes du serveur central
        String content = msg.getContent();
        
        if (content.startsWith("AUDIT")) {
            System.out.println("[" + nodeId + "] Requête d'audit reçue");
            
            // Répondre avec des informations détaillées
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            
            MonitoringData data = collectMetrics();
            String auditData = String.format("AUDIT_RESULT:node=%s:cpu=%.2f:bw=%.2f:processes=active", 
                                           nodeId, data.cpuUsage, data.bandwidth);
            reply.setContent(auditData);
            
            send(reply);
            System.out.println("✓ Réponse d'audit envoyée");
        }
    }
    
    protected void takeDown() {
        // Désenregistrer du DF
        try {
            DFService.deregister(this);
            System.out.println("[" + nodeId + "] Désenregistré du DF");
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println("Agent " + getLocalName() + " terminé");
    }
    
    // Classe interne pour les données de monitoring
    private class MonitoringData {
        double cpuUsage;
        double bandwidth;
    }
}