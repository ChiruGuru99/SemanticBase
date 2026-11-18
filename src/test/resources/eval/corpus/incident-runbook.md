# Halvorsen Labs Production Incident Runbook

Incidents are classified from Sev1 (full outage or data loss) to Sev4 (minor degradation). For a Sev1, the on-call
engineer must acknowledge the page within 5 minutes and open an incident channel immediately.

The incident commander posts an update on the public status page at least every 30 minutes until the incident is
resolved. Customer support is looped in for every Sev1 and Sev2.

A blameless postmortem is required for all Sev1 and Sev2 incidents and must be published within 5 business days after
resolution. Action items from the postmortem are tracked in the reliability backlog.
