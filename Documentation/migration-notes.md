# Migration Notes

## v1beta19 -> v1beta20

The CRD version v1beta20 introduces the GerritOperatorConfig CRD, that configures
the operator itself. The GerritOperatorConfig CRD can be installed using the
provided helm charts along with the Gerrit Operator. However, due to a limitation
in Helm this will not work while upgrading from v1beta19. In that case Helm will
try to install the CR before the CRD, which will fail. To avoid that the CR will
by default not be installed. This causes the ValidatingWebhookConfigurations not
to be reconciled. To bring the setup to the previous state use the following
steps:

1) Update the gerrit-operator helm chart including its dependency, the
   gerrit-operator-crds helm chart, e.g.:

   ```sh
   helm dependency update helm-charts/gerrit-operator
   helm upgrade --install gerrit-operator \
    helm-charts/gerrit-operator \
    -f values.yaml \
    -n gerrit-operator
   ```

   **NOTE:** Do not yet yet set the `config` key or any of its child keys.
   Setting it will cause helm to try to install the CR and the update will fail.

2) After the update has finished delete the potentially remaining
   ValidatingWebhookConfigurations and the Gerrit Operator's service. These will
   cause conflicts, if still present.

3) Set `config.enableValidatingWebhook: true` in your `values.yaml` file and update
   your helm chart deployment. This will install the GerritOperatorConfig and
   trigger the Gerrit Operator to install its Service and
   ValidatingWebhookConfigurations.
