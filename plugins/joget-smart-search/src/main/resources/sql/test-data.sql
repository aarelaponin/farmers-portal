-- ============================================================================
-- SMART FARMER SEARCH - TEST DATA
-- 
-- Insert test farmers directly into the search index for testing.
-- This bypasses the normal population process for quick testing.
--
-- Prerequisites: DDL from smart-search-ddl.sql must be executed first.
-- ============================================================================

-- Clear existing test data (optional - comment out if you want to keep existing)
-- DELETE FROM app_fd_farmer_search_index WHERE id LIKE 'TEST%';

-- ============================================================================
-- TEST DATA: 15 farmers with various attributes for testing
-- ============================================================================

INSERT INTO app_fd_farmer_search_index (
    id, c_search_name, c_search_text, c_name_soundex,
    c_national_id, c_phone_normalized,
    c_district_code, c_village, c_community_council,
    c_first_name, c_last_name, c_gender, c_date_of_birth,
    c_phone_display, c_district_name, c_cooperative_name, c_source_record_id
) VALUES

-- Berea District Farmers (Ha Matala)
('TEST001', 
 'mamosa motlomelo', 
 'mamosa motlomelo 123456789 26651234567 berea ha matala matala cc', 
 'M520 M340',
 '123456789', '26651234567', 'BER', 'Ha Matala', 'Matala CC',
 'Mamosa', 'Motlomelo', 'Female', '1985-03-15', 
 '+266 5123 4567', 'Berea', 'Matala Farmers Coop', 'FR001'),

('TEST002', 
 'mamosa mohapi', 
 'mamosa mohapi 111222333 26651112223 berea ha matala matala cc', 
 'M520 M100',
 '111222333', '26651112223', 'BER', 'Ha Matala', 'Matala CC',
 'Mamosa', 'Mohapi', 'Female', '1992-05-20', 
 '+266 5111 2223', 'Berea', 'Matala Farmers Coop', 'FR002'),

('TEST003', 
 'thabo mohapi', 
 'thabo mohapi 222333444 26652223334 berea ha mabote mabote cc', 
 'T100 M100',
 '222333444', '26652223334', 'BER', 'Ha Mabote', 'Mabote CC',
 'Thabo', 'Mohapi', 'Male', '1978-07-22', 
 '+266 5222 3334', 'Berea', 'Mabote Agri Coop', 'FR003'),

-- Maseru District Farmers
('TEST004', 
 'lerato sello', 
 'lerato sello 333444555 26653334445 maseru maseru central maseru urban', 
 'L630 S400',
 '333444555', '26653334445', 'MAS', 'Maseru Central', 'Maseru Urban',
 'Lerato', 'Sello', 'Female', '1990-11-08', 
 '+266 5333 4445', 'Maseru', 'Central Farmers', 'FR004'),

('TEST005', 
 'thabiso mohale', 
 'thabiso mohale 444555666 26654445556 maseru roma roma cc', 
 'T120 M400',
 '444555666', '26654445556', 'MAS', 'Roma', 'Roma CC',
 'Thabiso', 'Mohale', 'Male', '1988-09-12', 
 '+266 5444 5556', 'Maseru', 'Roma Agricultural', 'FR005'),

('TEST006', 
 'palesa mokoena', 
 'palesa mokoena 555666777 26655556667 maseru roma roma cc', 
 'P420 M250',
 '555666777', '26655556667', 'MAS', 'Roma', 'Roma CC',
 'Palesa', 'Mokoena', 'Female', '1995-02-28', 
 '+266 5555 6667', 'Maseru', 'Roma Agricultural', 'FR006'),

-- Leribe District Farmers
('TEST007', 
 'mpho letsie', 
 'mpho letsie 666777888 26656667778 leribe hlotse leribe urban', 
 'M100 L320',
 '666777888', '26656667778', 'LER', 'Hlotse', 'Leribe Urban',
 'Mpho', 'Letsie', 'Female', '1982-06-18', 
 '+266 5666 7778', 'Leribe', 'Hlotse Farmers', 'FR007'),

('TEST008', 
 'nthabiseng molapo', 
 'nthabiseng molapo 777888999 26657778889 leribe hlotse leribe urban', 
 'N312 M410',
 '777888999', '26657778889', 'LER', 'Hlotse', 'Leribe Urban',
 'Nthabiseng', 'Molapo', 'Female', '1987-04-05', 
 '+266 5777 8889', 'Leribe', 'Hlotse Farmers', 'FR008'),

-- Mafeteng District Farmers
('TEST009', 
 'moshoeshoe mofokeng', 
 'moshoeshoe mofokeng 888999000 26658889990 mafeteng mafeteng town mafeteng urban', 
 'M220 M125',
 '888999000', '26658889990', 'MAF', 'Mafeteng Town', 'Mafeteng Urban',
 'Moshoeshoe', 'Mofokeng', 'Male', '1975-12-10', 
 '+266 5888 9990', 'Mafeteng', 'Mafeteng Coop', 'FR009'),

('TEST010', 
 'lineo mokhethi', 
 'lineo mokhethi 999000111 26659990001 mafeteng ha ramabanta ramabanta cc', 
 'L500 M230',
 '999000111', '26659990001', 'MAF', 'Ha Ramabanta', 'Ramabanta CC',
 'Lineo', 'Mokhethi', 'Female', '1993-08-25', 
 '+266 5999 0001', 'Mafeteng', 'Ramabanta Agri', 'FR010'),

-- Additional test cases for fuzzy matching
('TEST011', 
 'mamosala motlomelo', 
 'mamosala motlomelo 111000222 26651110002 berea ha matala matala cc', 
 'M524 M340',
 '111000222', '26651110002', 'BER', 'Ha Matala', 'Matala CC',
 'Mamosala', 'Motlomelo', 'Female', '1989-01-30', 
 '+266 5111 0002', 'Berea', 'Matala Farmers Coop', 'FR011'),

-- Similar sounding names for Soundex testing
('TEST012', 
 'mohapy thabo', 
 'mohapy thabo 222000333 26652220003 berea ha mabote mabote cc', 
 'M100 T100',
 '222000333', '26652220003', 'BER', 'Ha Mabote', 'Mabote CC',
 'Mohapy', 'Thabo', 'Male', '1980-10-15', 
 '+266 5222 0003', 'Berea', 'Mabote Agri Coop', 'FR012'),

-- Butha-Buthe District
('TEST013', 
 'makhotso ntaote', 
 'makhotso ntaote 333000444 26653330004 butha-buthe butha-buthe town butha-buthe urban', 
 'M232 N300',
 '333000444', '26653330004', 'BUT', 'Butha-Buthe Town', 'Butha-Buthe Urban',
 'Makhotso', 'Ntaote', 'Female', '1991-07-08', 
 '+266 5333 0004', 'Butha-Buthe', 'Highland Farmers', 'FR013'),

-- Mohale's Hoek District
('TEST014', 
 'sello mothibi', 
 'sello mothibi 444000555 26654440005 mohaleshoek mohales hoek town mohaleshoek urban', 
 'S400 M310',
 '444000555', '26654440005', 'MOH', 'Mohales Hoek Town', 'Mohaleshoek Urban',
 'Sello', 'Mothibi', 'Male', '1983-03-22', 
 '+266 5444 0005', 'Mohales Hoek', 'Southern Coop', 'FR014'),

-- Qacha's Nek District
('TEST015', 
 'mampho sekonyela', 
 'mampho sekonyela 555000666 26655550006 qachasnek qachas nek town qachasnek urban', 
 'M510 S254',
 '555000666', '26655550006', 'QAC', 'Qachas Nek Town', 'Qachasnek Urban',
 'Mampho', 'Sekonyela', 'Female', '1986-11-30', 
 '+266 5555 0006', 'Qachas Nek', 'Eastern Highland Coop', 'FR015')

ON DUPLICATE KEY UPDATE
    c_search_name = VALUES(c_search_name),
    c_search_text = VALUES(c_search_text),
    c_name_soundex = VALUES(c_name_soundex),
    c_phone_normalized = VALUES(c_phone_normalized),
    c_last_updated = CURRENT_TIMESTAMP;


-- ============================================================================
-- VERIFICATION QUERIES
-- ============================================================================

-- Count test data
-- SELECT COUNT(*) as test_count FROM app_fd_farmer_search_index WHERE id LIKE 'TEST%';

-- View all test farmers
-- SELECT id, c_first_name, c_last_name, c_district_code, c_village, c_name_soundex
-- FROM app_fd_farmer_search_index 
-- WHERE id LIKE 'TEST%'
-- ORDER BY id;

-- Test exact ID search
-- SELECT * FROM app_fd_farmer_search_index WHERE c_national_id = '123456789';

-- Test name search with LIKE
-- SELECT * FROM app_fd_farmer_search_index WHERE c_search_name LIKE '%mamosa%';

-- Test Soundex search (should find Mohapi and Mohapy)
-- SELECT * FROM app_fd_farmer_search_index WHERE c_name_soundex LIKE '%M100%';

-- Test district filter
-- SELECT * FROM app_fd_farmer_search_index WHERE c_district_code = 'BER';

-- Test combined filters
-- SELECT * FROM app_fd_farmer_search_index 
-- WHERE c_district_code = 'BER' AND c_village = 'Ha Matala';
