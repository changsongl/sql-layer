SELECT CASE WHEN LCASE(NULL) THEN 'null' WHEN name IS NOT NULL THEN name ELSE 'default' END FROM customers
