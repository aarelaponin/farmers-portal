WITH farmer_data AS (
    SELECT
        d.c_name                                            AS district_name,
        COALESCE(cp.c_name, '—')                            AS centre_name,
        COALESCE(z.c_name, '—')                              AS zone_name,
        COUNT(DISTINCT fb.id)                               AS total_registered,

        SUM(CASE WHEN fb.c_gender = 'male'   THEN 1 ELSE 0 END) AS male_farmers,
        SUM(CASE WHEN fb.c_gender = 'female' THEN 1 ELSE 0 END) AS female_farmers,
        ROUND(
            SUM(CASE WHEN fb.c_gender = 'female' THEN 1 ELSE 0 END) * 100.0
            / NULLIF(COUNT(DISTINCT fb.id), 0), 1
        )                                                   AS female_pct,

        SUM(CASE
            WHEN fb.c_date_of_birth IS NOT NULL
                 AND fb.c_date_of_birth != ''
                 AND EXTRACT(YEAR FROM AGE(CURRENT_DATE, fb.c_date_of_birth::date)) < 35
            THEN 1 ELSE 0 END)                              AS youth_farmers,
        ROUND(
            SUM(CASE
                WHEN fb.c_date_of_birth IS NOT NULL
                     AND fb.c_date_of_birth != ''
                     AND EXTRACT(YEAR FROM AGE(CURRENT_DATE, fb.c_date_of_birth::date)) < 35
                THEN 1 ELSE 0 END) * 100.0
            / NULLIF(COUNT(DISTINCT fb.id), 0), 1
        )                                                   AS youth_pct,

        SUM(CASE WHEN fd.c_registrationStatus = 'VERIFIED' THEN 1 ELSE 0 END) AS verified,
        SUM(CASE WHEN fd.c_registrationStatus = 'PENDING'  THEN 1 ELSE 0 END) AS pending,
        SUM(CASE WHEN fd.c_registrationStatus = 'REJECTED' THEN 1 ELSE 0 END) AS rejected,

        ROUND(SUM(COALESCE(
            NULLIF(REGEXP_REPLACE(fl.c_totalAvailableLand, '[^0-9.\-]', '', 'g'), '')::numeric,
        0)), 2)                                             AS total_land_ha,
        ROUND(AVG(NULLIF(
            NULLIF(REGEXP_REPLACE(fl.c_totalAvailableLand, '[^0-9.\-]', '', 'g'), '')::numeric,
        0)), 2)                                             AS avg_farm_size_ha,
        ROUND(SUM(COALESCE(
            NULLIF(REGEXP_REPLACE(fl.c_cultivatedLand, '[^0-9.\-]', '', 'g'), '')::numeric,
        0)), 2)                                             AS total_cultivated_ha,

        MIN(fb.dateCreated)                                 AS date_created,
        0                                                   AS sort_order

    FROM app_fd_farmerBasicInfo fb
    LEFT JOIN app_fd_farm_location fl
        ON fl.c_parent_id = fb.c_parent_id
    LEFT JOIN app_fd_farmer_declaration fd
        ON fd.c_parent_id = fb.c_parent_id
    LEFT JOIN app_fd_md03district d
        ON d.c_code = fl.c_district
    LEFT JOIN app_fd_md37collectionpoint cp
        ON cp.c_code = fl.c_resource_center
    LEFT JOIN app_fd_md04agroecologicalzo z
        ON z.c_code = fl.c_agroecologicalzone

    WHERE 1=1
        AND (NULLIF('#requestParam.d-5814095-fn_date_from?sql#', '') IS NULL
             OR fb.dateCreated >= NULLIF('#requestParam.d-5814095-fn_date_from?sql#', '')::timestamp)
        AND (NULLIF('#requestParam.d-5814095-fn_date_to?sql#', '') IS NULL
             OR fb.dateCreated < NULLIF('#requestParam.d-5814095-fn_date_to?sql#', '')::timestamp + INTERVAL '1 day')
        AND (NULLIF('#requestParam.d-5814095-fn_district_name?sql#', '') IS NULL
             OR fl.c_district = ANY(string_to_array('#requestParam.d-5814095-fn_district_name?sql#', ';')))
        AND (NULLIF('#requestParam.d-5814095-fn_centre_name?sql#', '') IS NULL
             OR fl.c_resource_center = ANY(string_to_array('#requestParam.d-5814095-fn_centre_name?sql#', ';')))
        AND (NULLIF('#requestParam.d-5814095-fn_zone_name?sql#', '') IS NULL
             OR fl.c_agroecologicalzone = ANY(string_to_array('#requestParam.d-5814095-fn_zone_name?sql#', ';')))

    GROUP BY d.c_name, cp.c_name, z.c_name
)

SELECT
    district_name,
    centre_name,
    zone_name,
    total_registered,
    male_farmers || ' <span style="color:#6b7280;font-size:0.85em;">(' ||
        COALESCE(ROUND(male_farmers * 100.0 / NULLIF(total_registered, 0), 1)::text, '0') || '%)</span>' AS male_display,
    female_farmers || ' <span style="color:#6b7280;font-size:0.85em;">(' ||
        COALESCE(female_pct::text, '0') || '%)</span>' AS female_display,
    youth_farmers || ' <span style="color:#6b7280;font-size:0.85em;">(' ||
        COALESCE(youth_pct::text, '0') || '%)</span>' AS youth_display,
    '<span style="background:#27ae60;color:#fff;padding:2px 8px;border-radius:10px;font-size:0.85em;">' || COALESCE(verified::text, '0') || '</span>' AS verified_display,
    '<span style="background:#f39c12;color:#fff;padding:2px 8px;border-radius:10px;font-size:0.85em;">' || COALESCE(pending::text, '0') || '</span>'  AS pending_display,
    '<span style="background:#e74c3c;color:#fff;padding:2px 8px;border-radius:10px;font-size:0.85em;">' || COALESCE(rejected::text, '0') || '</span>' AS rejected_display,
    total_land_ha,
    avg_farm_size_ha,
    total_cultivated_ha,
    date_created
FROM (
    SELECT * FROM farmer_data

    UNION ALL

    SELECT
        'TOTAL'                  AS district_name,
        ''                       AS centre_name,
        ''                       AS zone_name,
        SUM(total_registered)    AS total_registered,
        SUM(male_farmers)        AS male_farmers,
        SUM(female_farmers)      AS female_farmers,
        ROUND(SUM(female_farmers) * 100.0
            / NULLIF(SUM(total_registered), 0), 1) AS female_pct,
        SUM(youth_farmers)       AS youth_farmers,
        ROUND(SUM(youth_farmers) * 100.0
            / NULLIF(SUM(total_registered), 0), 1) AS youth_pct,
        SUM(verified)            AS verified,
        SUM(pending)             AS pending,
        SUM(rejected)            AS rejected,
        SUM(total_land_ha)       AS total_land_ha,
        ROUND(AVG(NULLIF(avg_farm_size_ha, 0)), 2) AS avg_farm_size_ha,
        SUM(total_cultivated_ha) AS total_cultivated_ha,
        MIN(date_created)        AS date_created,
        1                        AS sort_order
    FROM farmer_data
) combined
WHERE 1=1
ORDER BY
    CASE
        WHEN district_name = 'TOTAL' THEN 2
        WHEN district_name IS NULL OR district_name = '' THEN 1
        ELSE 0
    END,
    district_name, centre_name, zone_name
