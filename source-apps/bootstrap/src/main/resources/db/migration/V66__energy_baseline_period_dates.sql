-- Baseline values need a traceable reference period; leave existing records unchanged.
ALTER TABLE src_energy.energy_equipment_usage
    ADD COLUMN baseline_period_start_date DATE NULL,
    ADD COLUMN baseline_period_end_date DATE NULL;

ALTER TABLE src_energy.energy_workshop_usage
    ADD COLUMN baseline_period_start_date DATE NULL,
    ADD COLUMN baseline_period_end_date DATE NULL;
