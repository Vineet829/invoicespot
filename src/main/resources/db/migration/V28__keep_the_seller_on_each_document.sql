-- The business's details and the name of its tax, as each document went out
-- with them. Until now a document showed the business's current details, so
-- existing documents keep exactly what they show today. From now on a
-- document keeps what it was made with, however the business changes later.
ALTER TABLE documents
    ADD COLUMN seller_name        VARCHAR(255),
    ADD COLUMN seller_legal_name  VARCHAR(255),
    ADD COLUMN seller_email       VARCHAR(254),
    ADD COLUMN seller_phone       VARCHAR(30),
    ADD COLUMN seller_address     VARCHAR(255),
    ADD COLUMN seller_city        VARCHAR(255),
    ADD COLUMN seller_state       VARCHAR(255),
    ADD COLUMN seller_postal_code VARCHAR(30),
    ADD COLUMN seller_country     VARCHAR(255),
    ADD COLUMN seller_logo        VARCHAR(500),
    ADD COLUMN seller_tax_id      VARCHAR(50),
    ADD COLUMN tax_label          VARCHAR(20) NOT NULL DEFAULT 'Tax';

UPDATE documents d
SET seller_name = b.name,
    seller_legal_name = b.legal_name,
    seller_email = b.email,
    seller_phone = b.phone_number,
    seller_address = b.address,
    seller_city = b.city,
    seller_state = b.state,
    seller_postal_code = b.postal_code,
    seller_country = b.country,
    seller_logo = b.logo,
    seller_tax_id = b.tax_id
FROM businesses b
WHERE b.pkid = d.business_pkid;

UPDATE documents d
SET tax_label = s.tax_label
FROM business_settings s
WHERE s.business_pkid = d.business_pkid;
