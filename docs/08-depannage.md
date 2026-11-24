# 8. Dépannage

## 8.1 Application

| Symptôme | Cause probable | Action |
|---|---|---|
| Un service s'arrête au démarrage : « jwt-secret doit être défini » | `JWT_SECRET` absent ou < 32 caractères | Compléter `.env` ou le groupe `makzeo-secrets` |
| Connexion admin impossible sur une base neuve | `ADMIN_PASSWORD` absent au premier démarrage | Le définir, supprimer le volume (`docker compose down -v`) et redémarrer |
| 401 sur toutes les requêtes après redéploiement | `JWT_SECRET` modifié : anciens jetons invalides | Se reconnecter |
| 429 à la connexion | Plus de 10 tentatives par minute | Attendre une minute |
| Erreur CORS dans le navigateur | Origine absente de `CORS_ALLOWED_ORIGINS` | Ajouter l'URL exacte utilisée dans le navigateur |
| « Schema-validation: missing table » | Migration Flyway non appliquée | Consulter les journaux Flyway du service ; ne jamais modifier une migration déjà appliquée |
| Achats IA : « IA non configurée » | `AI_API_KEY` vide | Renseigner la clé dans `.env` ou `makzeo-secrets` ; le module reste utilisable sans IA |
| Achats IA : RFQ au statut « Échec » | SMTP injoignable ou refus d'authentification | Journaux de `procurement-service` ; vérifier `SMTP_*` ou Mailpit (<http://localhost:8025>) |
| procurement-service : « database procurement_db does not exist » | Volume PostgreSQL antérieur au module | `docker compose up procurement-db-init` (Docker) ; en Kubernetes, le conteneur d'initialisation la crée |

## 8.2 Cluster Kubernetes

```bash
kubectl get nodes
kubectl -n stock-management get pods -o wide
kubectl -n stock-management describe pod <pod>
kubectl -n stock-management logs <pod> --previous
kubectl -n stock-management get events --sort-by=.lastTimestamp | tail -20
```

| Symptôme | Cause probable | Action |
|---|---|---|
| Nœud `NotReady` | Calico non démarré, swap actif, VM suspendue | `kubectl -n kube-system get pods`, `swapon --show`, `journalctl -u kubelet` |
| Pod `ImagePullBackOff` | Secret `nexus-registry` absent ou containerd sans accès HTTP au registre | `kubectl -n stock-management get secret nexus-registry` ; vérifier `/etc/containerd/certs.d/192.168.80.10:8083/hosts.toml` puis `sudo systemctl restart containerd` |
| Pod `CrashLoopBackOff` | Variable manquante, base inaccessible | `kubectl logs --previous` |
| Pod `Pending` | Ressources insuffisantes ou PVC non lié | `kubectl describe pod` ; `kubectl get pvc` ; augmenter la RAM des workers |
| PVC `Pending` | local-path absent ou non par défaut | `kubectl get storageclass` |
| HPA `<unknown>` | metrics-server non prêt | `kubectl -n kube-system logs deploy/metrics-server` |
| Application injoignable par son nom | Ingress non démarré ou DNS nip.io indisponible | `kubectl -n ingress-nginx get pods` ; ajouter le nom dans le fichier `hosts` |
| Jeton `kubeadm join` expiré | Validité de 24 h | `kubeadm token create --print-join-command` |

## 8.3 Pipeline Azure DevOps

| Symptôme | Cause probable | Action |
|---|---|---|
| « No agent found in pool Makzeo-OnPrem » | Agent arrêté ou master éteint | `sudo systemctl status 'vsts.agent.*'` sur le master (outils DevOps) |
| « Variable group was not found or is not authorized » | Nom différent ou pipeline non autorisé | Nom exact `makzeo-secrets` ; *Library > makzeo-secrets > Pipeline permissions* |
| Maven : « 401 Unauthorized » vers Nexus | `NEXUS_CI_PASSWORD` incorrect | Vérifier le compte `ci-deployer` dans Nexus |
| Maven : « 400 Bad Request » au `deploy` | Publication d'une version release déjà existante | Utiliser une version `-SNAPSHOT` ou incrémenter la version |
| SonarQube : « Quality Gate failed » | Qualité insuffisante sur le nouveau code | Consulter le projet dans SonarQube, corriger, relancer |
| SonarQube : « Not authorized » | `SONAR_TOKEN` invalide ou révoqué | Générer un nouveau jeton (*My Account > Security*) |
| `docker push` : « http: server gave HTTP response to HTTPS client » | Registre non déclaré en HTTP | `/etc/docker/daemon.json` de le master (outils DevOps) : `insecure-registries`, puis `sudo systemctl restart docker` |
| Trivy bloque le build | Vulnérabilité corrigeable détectée | Mettre à jour la dépendance ou l'image de base |
| Deploy : « Forbidden » | Ressource hors des droits de `ci-deployer` | Compléter `infra/vmware/ci-deployer-rbac.yaml` et l'appliquer avec le compte administrateur |
| Deploy : « rollout status ... timed out » | Pod non prêt | Voir 8.2 |
| Monitoring : cibles < 5 | ServiceMonitor non pris en compte | Prometheus > *Status > Targets* ; vérifier `serviceMonitorSelectorNilUsesHelmValues: false` |

## 8.4 SonarQube et Nexus

| Symptôme | Action |
|---|---|
| SonarQube ne démarre pas (« max virtual memory areas ») | `sudo sysctl -w vm.max_map_count=524288` (déjà persistant après `install-devops-tools.sh`) |
| Nexus lent au premier démarrage | Normal (2 à 4 minutes) ; `sudo docker compose logs -f nexus` |
| Espace disque saturé sur le master (outils DevOps) | `docker system prune -af` ; *Nexus > Tasks* : nettoyage des images anciennes |
