ALTER TABLE yearly_workflow ADD COLUMN IF NOT EXISTS requested_return_on date;
ALTER TABLE yearly_workflow ADD COLUMN IF NOT EXISTS settlement_accepted_at timestamp with time zone;
ALTER TABLE yearly_workflow ADD COLUMN IF NOT EXISTS settlement_accepted_by bigint;
ALTER TABLE yearly_workflow ADD COLUMN IF NOT EXISTS completed_at timestamp with time zone;
ALTER TABLE yearly_incident ADD COLUMN IF NOT EXISTS reported_category varchar(2000);
ALTER TABLE yearly_incident ADD COLUMN IF NOT EXISTS proposed_category varchar(2000);
