UPDATE documents d
SET sub_total = lines.sub_total,
    sales_tax = round(lines.sub_total * d.rates / 100, 2),
    total = lines.sub_total + round(lines.sub_total * d.rates / 100, 2)
FROM (SELECT doc.pkid,
             coalesce(sum(round(coalesce(item.quantity, 0) * coalesce(item.unit_price, 0)
                     * (100 - item.discount) / 100, 2)), 0) AS sub_total
      FROM documents doc
      LEFT JOIN document_billing_items item ON item.document_pkid = doc.pkid
      GROUP BY doc.pkid) lines
WHERE lines.pkid = d.pkid;

UPDATE documents d
SET total_amount_received = CASE WHEN d.document_type = 'Receipt' THEN d.total ELSE paid.received END
FROM (SELECT doc.pkid, coalesce(sum(payment.amount_paid), 0) AS received
      FROM documents doc
      LEFT JOIN document_payment_records payment ON payment.document_pkid = doc.pkid
      GROUP BY doc.pkid) paid
WHERE paid.pkid = d.pkid;

UPDATE documents
SET status = CASE
    WHEN document_type = 'Receipt' THEN 'Paid'
    WHEN total > 0 AND total_amount_received >= total THEN 'Paid'
    WHEN total_amount_received > 0 THEN 'Not Fully Paid'
    ELSE 'Not Paid'
END;

ALTER TABLE documents
    ALTER COLUMN sub_total SET DEFAULT 0,
    ALTER COLUMN sub_total SET NOT NULL,
    ALTER COLUMN sales_tax SET DEFAULT 0,
    ALTER COLUMN sales_tax SET NOT NULL,
    ALTER COLUMN total SET DEFAULT 0,
    ALTER COLUMN total SET NOT NULL,
    ALTER COLUMN total_amount_received SET DEFAULT 0,
    ALTER COLUMN total_amount_received SET NOT NULL;
