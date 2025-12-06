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
        
        // Ajouter comportement de réception de messages (unifié)
        addBehaviour(new ReceiveMessagesBehaviour());
        
        // Ajouter comportement d'analyse périodique (pour cleanup et stats)
        addBehaviour(new PeriodicAnalysisBehaviour());
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
    
    // COMPORTEMENT UNIFIÉ - Reçoit TOUS les messages INFORM
    private class ReceiveMessagesBehaviour extends CyclicBehaviour {
        
        public void action() {
            // Recevoir TOUS les messages INFORM
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = receive(mt);
            
            if (msg != null) {
                String content = msg.getContent();
                
                // Router selon le type de message
                if (content != null) {
                    if (content.startsWith("ALERT:")) {
                        handleAlert(content);
                    } else if (content.startsWith("AUDIT_REPORT:")) {
                        handleAuditReport(content);
                    } else {
                        System.out.println("⚠ Message non reconnu: " + content);
                    }
                }
            } else {
                block();
            }
        }
        
        private void handleAlert(String alertContent) {
            System.out.println("\n⚠ ALERTE REÇUE: " + alertContent);
            processAlert(alertContent);
            
            // ANALYSE IMMÉDIATE après chaque alerte!
            checkAndDeployAgent(alertContent);
        }
        
        private void handleAuditReport(String reportContent) {
            System.out.println("\n📊 RAPPORT D'AUDIT REÇU:");
            System.out.println(reportContent);
            processAuditReport(reportContent);
        }
    }
    
    // Comportement périodique pour statistiques et cleanup
    private class PeriodicAnalysisBehaviour extends TickerBehaviour {
        
        public PeriodicAnalysisBehaviour() {
            super(CentralServerAgent.this, 30000); // Toutes les 30 secondes
        }
        
        protected void onTick() {
            System.out.println("\n--- Analyse périodique des alertes ---");
            
            if (alerts.isEmpty()) {
                System.out.println("Aucune alerte active");
                return;
            }
            
            // Afficher les statistiques
            for (String nodeId : alerts.keySet()) {
                AlertData alert = alerts.get(nodeId);
                System.out.println("Node " + nodeId + ": " + alert.count + " alertes totales");
            }
            
            // Cleanup: supprimer les alertes anciennes (> 5 minutes)
            long now = System.currentTimeMillis();
            alerts.entrySet().removeIf(entry -> 
                (now - entry.getValue().lastSeen) > 300000
            );
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
    
    // VÉRIFICATION IMMÉDIATE après chaque alerte
    private void checkAndDeployAgent(String alertContent) {
        try {
            String[] parts = alertContent.split(":");
            if (parts.length < 2) return;
            
            String nodeId = parts[1];
            AlertData alert = alerts.get(nodeId);
            
            // Déployer SEULEMENT si: count == 3 AND pas déjà en audit
            if (alert != null && alert.count == 3 && !auditedNodes.contains(nodeId)) {
                System.out.println("🚨 ANOMALIE CONFIRMÉE sur " + nodeId + " - Déploiement IMMÉDIAT de l'agent mobile");
                deployMobileAgent(nodeId);
                auditedNodes.add(nodeId);
                
                // Réinitialiser le compteur pour éviter re-déploiements
                alert.count = 0;
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la vérification: " + e.getMessage());
        }
    }
    
    private void processAuditReport(String reportContent) {
        // Parser: "AUDIT_REPORT:node1|Location:Container-node1|Time:...|Status:...|Details:..."
        System.out.println("Traitement du rapport d'audit...");
        
        try {
            String[] parts = reportContent.split("\\|");
            if (parts.length > 0) {
                String nodeInfo = parts[0].split(":")[1];
                System.out.println("✓ Audit de " + nodeInfo + " traité avec succès");
                
                // Retirer de la liste des audités pour permettre de futurs audits
                auditedNodes.remove(nodeInfo);
            }
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement du rapport: " + e.getMessage());
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