# Migration Notes

## v1beta19 -> v1beta20

The CRD version v1beta20 introduces the GerritOperatorConfig CRD, that configures
the operator itself. The GerritOperatorConfig CRD can be installed using the
provided helm charts along with the Gerrit Operator. However, due to a limitation
in Helm this will not work while upgrading from v1beta19. In that case Helm will
try to install the CR before the CRD, which will fail. To avoid that the CR will
only be installed, if the CRD is already available in the target cluster. Thus,
on a first installation or upgrade to v1beta20 the chart needs to be installed
twice. The first installation will install the CRD and the operator with the
default configuration and the second will install the CustomResource to apply the
configuration.
