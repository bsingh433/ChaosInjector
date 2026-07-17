FROM grafana/grafana:11.1.0

# Provisioned Prometheus datasource + auto-loaded dashboards (no manual setup).
COPY provisioning/ /etc/grafana/provisioning/
COPY dashboards/ /var/lib/grafana/dashboards/

# Demo defaults (override in compose/.env as needed).
ENV GF_SECURITY_ADMIN_USER=admin \
    GF_SECURITY_ADMIN_PASSWORD=admin \
    GF_USERS_ALLOW_SIGN_UP=false

EXPOSE 3000
