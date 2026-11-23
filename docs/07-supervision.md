# 7. Supervision : Prometheus et Grafana

## 7.1 Chaîne de collecte

```
 microservice (Actuator + Micrometer)          kube-prometheus-stack (namespace monitoring)
 /actuator/prometheus  ◄── scrape 30 s ──  Prometheus ──► Alertmanager
          ▲                                     │
 ServiceMonitor makzeo-backend                  └──► Grafana : tableau de bord "Makzeo - Vue d'ensemble"
 (label app.kubernetes.io/component=backend)
```

- Chaque service expose ses métriques au format Prometheus sur `/actuator/prometheus` (route
  publique côté service, mais non publiée par l'Ingress ni par la passerelle).
- Toutes les métriques portent le label `application` (nom du service).
- Les requêtes HTTP sont mesurées avec un histogramme (`http_server_requests_seconds_bucket`)
  qui permet de calculer les percentiles de latence.
- Les nœuds, pods et conteneurs sont supervisés par node-exporter et kube-state-metrics.

## 7.2 Composants livrés avec l'application

Dans `deploy/k8s/overlays/onprem/monitoring/` :

| Fichier | Objet |
|---|---|
| `servicemonitor.yaml` | Découverte automatique des 5 services backend |
| `prometheusrule.yaml` | Règles d'alerte de l'application |
| `makzeo-overview.json` | Tableau de bord Grafana, chargé automatiquement (ConfigMap `grafana_dashboard: "1"`) |

Ils sont déployés par le pipeline avec l'application : la supervision évolue avec le code.

## 7.3 Alertes

| Alerte | Condition | Sévérité |
|---|---|---|
| MakzeoServiceDown | Un service ne répond plus à la collecte depuis 2 min | critical |
| MakzeoPodRestarting | Plus de 3 redémarrages d'un pod en 15 min | warning |
| MakzeoHighErrorRate | Plus de 5 % de réponses 5xx sur 5 min | warning |
| MakzeoHighLatency | Latence p95 supérieure à 1 s sur 10 min | warning |
| MakzeoJvmHeapHigh | Mémoire heap utilisée à plus de 90 % pendant 10 min | warning |

Consultation : Prometheus > *Alerts*, ou Grafana > *Alerting*. L'envoi de notifications (e-mail,
Teams, Slack) se configure dans Alertmanager (`alertmanager.config` des valeurs Helm).

## 7.4 Tableau de bord Grafana

`http://grafana.192.168.80.11.nip.io` > *Dashboards* > **Makzeo - Vue d'ensemble** :

- **Disponibilité** : services disponibles, requêtes par seconde, taux d'erreurs 5xx,
  redémarrages de pods ;
- **Trafic HTTP** : requêtes par seconde, latence p95 et erreurs 5xx par service ;
- **Métier** : mouvements de stock enregistrés par minute ;
- **JVM et base de données** : mémoire heap, CPU, connexions JDBC actives (HikariCP), pauses
  du ramasse-miettes.

Les tableaux de bord fournis par kube-prometheus-stack (*Kubernetes / Compute Resources /
Namespace (Pods)*, *Node Exporter / Nodes*) complètent la vue côté infrastructure.

## 7.5 Requêtes PromQL utiles

```promql
# Services collectés
up{namespace="stock-management", job=~".+-service"}

# Requêtes par seconde par service
sum by (application) (rate(http_server_requests_seconds_count{namespace="stock-management"}[5m]))

# Latence p95 par service
histogram_quantile(0.95, sum by (application, le) (
  rate(http_server_requests_seconds_bucket{namespace="stock-management"}[5m])))

# Tentatives de connexion refusées
sum(rate(http_server_requests_seconds_count{application="auth-service", uri="/api/auth/login", status="401"}[5m]))
```

## 7.6 Vérification automatique

Le stage **Monitoring** du pipeline exécute `ci/scripts/check-prometheus.sh` : il interroge
Prometheus via le proxy de l'API Kubernetes, attend que les 5 services soient collectés et échoue
si une alerte critique Makzeo est active.
