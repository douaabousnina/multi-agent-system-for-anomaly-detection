package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.Location;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.*;
import jade.wrapper.AgentContainer;

public class MobileAuditAgent extends Agent {

    private String targetNode;
    private String originContainer;

    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            targetNode = (String) args[0];
        }

        originContainer = here().getName();
        System.out.println("Agent Mobile d'audit créé: " + getLocalName());
        System.out.println("Container actuel: " + originContainer);
        System.out.println("Cible: " + targetNode);

        // Effectuer l'audit immédiatement (pas de migration)
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                performAudit();
            }
        });
    }

    
    private void performAudit() {
        System.out.println("*** DÉBUT DE L'AUDIT SUR " + targetNode + " ***");
        
        try {
            // Simuler la collecte d'informations détaillées
            Thread.sleep(2000);
            
            System.out.println("Collecte des métriques système...");
            System.out.println("Analyse des processus actifs...");
            System.out.println("Vérification du trafic réseau...");
            System.out.println("Scan des ports ouverts...");
            
            // Créer le rapport d'audit
            StringBuilder report = new StringBuilder();
            report.append("AUDIT_REPORT:").append(targetNode).append("|");
            report.append("Location:").append(here().getName()).append("|");
            report.append("Time:").append(System.currentTimeMillis()).append("|");
            report.append("Status:ANOMALY_CONFIRMED|");
            report.append("Details:High CPU usage detected, suspicious network activity");
            
            // Envoyer le rapport au serveur central
            sendReportToServer(report.toString());
            
            System.out.println("*** AUDIT TERMINÉ ***");
            
        } catch (Exception e) {
            System.err.println("Erreur pendant l'audit: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Supprimer l'agent après l'audit
        doDelete();
    }

    private void sendReportToServer(String reportContent) {
        try {
            // Trouver le serveur central via DF
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("server-service");
            template.addServices(sd);
            
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                ACLMessage report = new ACLMessage(ACLMessage.INFORM);
                report.addReceiver(result[0].getName());
                report.setContent(reportContent);
                send(report);
                System.out.println("Rapport d'audit envoyé au serveur central");
            } else {
                System.err.println("Impossible de trouver le serveur central");
            }
        } catch (FIPAException e) {
            System.err.println("Erreur lors de l'envoi du rapport: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    protected void takeDown() {
        System.out.println("Agent mobile " + getLocalName() + " terminé");
    }
}