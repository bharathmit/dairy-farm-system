-- "Other" produce sales: number of units × unit price − discount.
ALTER TABLE production_record ADD COLUMN discount NUMERIC(12, 2) CHECK (discount >= 0);
UPDATE production_record SET unit = 'NOS' WHERE animal_id IS NULL AND product = 'OTHER';
