package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.*;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import java.util.*;

public class CentralServerAgent extends Agent {
    
    private Map<String, AlertData> alerts = new HashMap<>();
    private Set<String> auditedNodes = new HashSet<>();
    
    protected void setup() {
        System.out.println("========================================");
        System.out.println("Agent Serveur Central démarré: " + getLocalName());
        System.out.println("Container: " + here().getName());
        System.out.println("========================================");
        
        // Enregistrer dans le DF
        registerInDF();
        
        // Ajouter comportement de réception d'alertes
        addBehaviour(new ReceiveAlertsBehaviour());
        
        // Ajouter comportement d'analyse périodique
        addBehaviour(new AnalyzeBehaviour());
        
        // Ajouter comportement de réception des rapports d'audit
        addBehaviour(new ReceiveAuditReportsBehaviour());
    }
    
    private void registerInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setName("server-service");
        sd.setType("server-service");
        dfd.addServices(sd);
        
        try {
            DFService.register(this, dfd);
            System.out.println("✓ Serveur enregistré dans le Directory Facilitator");
        } catch (FIPAException e) {
            System.err.println("✗ Erreur lors de l'enregistrement DF: " + e.getMessage());
        }
    }
    
    private class ReceiveAlertsBehaviour extends CyclicBehaviour {
        
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchContent("ALERT*")
            );
            ACLMessage msg = receive(mt);
            
            if (msg != null) {
                String content = msg.getContent();
                System.out.println("\n⚠ ALERTE REÇUE: " + content);
                processAlert(content);
            } else {
                block();
            }
        }
    }
    
    private class ReceiveAuditReportsBehaviour extends CyclicBehaviour {
        
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchContent("AUDIT_REPORT*")
            );
            ACLMessage msg = receive(mt);
            
            if (msg != null) {
                String content = msg.getContent();
                System.out.println("\n📊 RAPPORT D'AUDIT REÇU:");
                System.out.println(content);
                processAuditReport(content);
            } else {
                block();
            }
        }
    }
    
    private class AnalyzeBehaviour extends TickerBehaviour {
        
        public AnalyzeBehaviour() {
            super(CentralServerAgent.this, 30000); // Toutes les 30 secondes
        }
        
        protected void onTick() {
            System.out.println("\n--- Analyse périodique des alertes ---");
            
            if (alerts.isEmpty()) {
                System.out.println("Aucune alerte active");
                return;
            }
            
            // Analyser toutes les alertes
            for (String nodeId : alerts.keySet()) {
                AlertData alert = alerts.get(nodeId);
                
                System.out.println("Node " + nodeId + ": " + alert.count + " alertes");
                
                // Si 3 alertes ou plus et pas encore audité
                if (alert.count >= 3 && !auditedNodes.contains(nodeId)) {
                    System.out.println("🚨 ANOMALIE CONFIRMÉE sur " + nodeId + " - Déploiement agent mobile");
                    deployMobileAgent(nodeId);
                    auditedNodes.add(nodeId);
                    
                    // Réinitialiser le compteur après déploiement
                    alert.count = 0;
                }
            }
        }
    }
    
    private void processAlert(String alertContent) {
        try {
            // Parser: "ALERT:node1:CPU=85.5:BW=45.2"
            String[] parts = alertContent.split(":");
            if (parts.length < 2) return;
            
            String nodeId = parts[1];
            
            AlertData alert = alerts.getOrDefault(nodeId, new AlertData());
            alert.count++;
            alert.lastSeen = System.currentTimeMillis();
            alerts.put(nodeId, alert);
            
            System.out.println("Compteur d'alertes pour " + nodeId + ": " + alert.count);
            
            // Sauvegarder dans la base de données
            // TODO: Appeler DatabaseManager
            
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement de l'alerte: " + e.getMessage());
        }
    }
    
    private void processAuditReport(String reportContent) {
        // Parser: "AUDIT_REPORT:node1|Location:Container-node1|Time:...|Status:...|Details:..."
        System.out.println("Traitement du rapport d'audit...");
        
        // Retirer le nœud de la liste des audités après un certain temps
        // pour permettre de futures audits si nécessaire
        String[] parts = reportContent.split("\\|");
        if (parts.length > 0) {
            String nodeInfo = parts[0].split(":")[1];
            System.out.println("✓ Audit de " + nodeInfo + " traité avec succès");
        }
    }
    
    private void deployMobileAgent(String targetNode) {
        try {
            System.out.println("\n🤖 Création d'un agent mobile pour auditer: " + targetNode);
            
            // Créer un agent mobile avec le nom unique
            String agentName = "audit-agent-" + targetNode + "-" + System.currentTimeMillis();
            Object[] args = new Object[]{targetNode};
            
            AgentController ac = getContainerController().createNewAgent(
                agentName,
                "agents.MobileAuditAgent",
                args
            );
            
            ac.start();
            System.out.println("✓ Agent mobile " + agentName + " créé et démarré");
            
        } catch (StaleProxyException e) {
            System.err.println("✗ Erreur lors de la création de l'agent mobile: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println("Agent Serveur Central terminé");
    }
    
    private class AlertData {
        int count = 0;
        long lastSeen;
    }
}