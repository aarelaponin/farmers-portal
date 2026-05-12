package global.govstack.parcelzonecentring.binder;

import global.govstack.parcelzonecentring.Build;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;

/**
 * Custom load binder for the parcelGeometry form. Adds render-time enrichment
 * of {@code auto_center_lat / auto_center_lon} from the sibling parcelLocation's
 * (district, agroEcologicalZone) pair, even when no parcelGeometry row exists
 * yet in the DB.
 *
 * <p><b>Why this is needed.</b> For NEW parcel registrations under
 * {@code partiallyStore=true}, the citizen clicks Next from the General Data
 * tab and lands on the Location tab while the wizard is still in
 * {@code _mode=add} with no primary key in the URL. parcelGeometry's stock
 * loadBinder runs with an empty primary key — it can't find any row keyed by
 * the wizard's id (which doesn't exist yet) and returns an empty FormData.
 * The GIS widget then renders with empty {@code auto_center_lat/lon} HiddenFields
 * and falls back to its default coordinates.
 *
 * <p>The {@link ParcelLocationAutoCenterBinder} writes auto_center to a
 * parcelGeometry row inline with parcelLocation's commit, but the next render
 * of the Location tab can't find that row because the wizard's primary key is
 * still empty.
 *
 * <p>This loadBinder closes the gap by computing auto_center directly from
 * sibling FormData at render time — independent of any DB row. It walks the
 * root form to find the parcelLocation tab subform's district and
 * agroEcologicalZone elements, reads their values from FormData, looks up the
 * centroid in {@code md_district_eco_zone}, and injects auto_center_lat/lon
 * into the FormRowSet returned to the GIS widget.
 *
 * <p>Works in three scenarios:
 * <ul>
 *   <li><b>New parcel, first visit to Location tab</b> — no existing row;
 *       we synthesise a stub with auto_center.</li>
 *   <li><b>New parcel, second visit (after our storeBinder bootstrap)</b> —
 *       row exists with auto_center; super.load() returns it; we don't
 *       overwrite (idempotent).</li>
 *   <li><b>Existing parcel, edit mode</b> — row exists; super.load() returns
 *       all geometry/area/etc.; if auto_center is empty (legacy data), we
 *       inject; otherwise leave alone.</li>
 * </ul>
 *
 * <p><b>Defensive contract.</b> Wrapped in a try/catch. If anything throws,
 * we fall back to whatever super.load() returned. The map will render at
 * default coordinates rather than centroid — degraded but functional.
 */
public class ParcelGeometryAutoCenterLoadBinder extends WorkflowFormBinder implements FormLoadBinder {

    private static final String CLASS_NAME = ParcelGeometryAutoCenterLoadBinder.class.getName();

    private static final String ZONE_LOOKUP_FORM = "md_district_eco_zone";

    @Override public String getName()        { return "Parcel Geometry Auto-Center Load Binder"; }
    @Override public String getVersion()     { return "8.1-SNAPSHOT (" + Build.STAMP + ")"; }
    @Override public String getLabel()       { return getName(); }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getDescription() {
        return "Extends WorkflowFormBinder. On parcelGeometry load, reads the "
             + "sibling parcelLocation's (district, agroEcologicalZone) directly "
             + "from FormData (no DB lookup of parcelLocation needed) and injects "
             + "auto_center_lat/lon from md_district_eco_zone so the GIS widget "
             + "centres the map even on the very first visit to the Location tab "
             + "of a new parcel under partiallyStore=true. ["
             + Build.STAMP + "]";
    }

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        LogUtil.info(CLASS_NAME, "load() entry — primaryKey="
                + (primaryKey == null ? "null" : "'" + primaryKey + "'"));

        // Phase 1: stock load. May return empty (new parcel) or a populated
        // row (edit mode / after our storeBinder bootstrap).
        FormRowSet rs = super.load(element, primaryKey, formData);
        LogUtil.info(CLASS_NAME, "super.load() returned "
                + (rs == null ? "null" : (rs.isEmpty() ? "empty" : rs.size() + " row(s)")));

        // Phase 2: enrichment. Wrapped in try/catch — failure here only
        // costs centring, not the form render.
        try {
            rs = enrichWithCentroid(element, formData, rs);
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t,
                    "ParcelGeometryAutoCenterLoadBinder enrichment failed: " + t.getMessage());
        }
        return rs;
    }

    private FormRowSet enrichWithCentroid(Element element, FormData formData, FormRowSet rs) {
        String district = null;
        String zone = null;

        // Strategy 1: walk root form's element tree for the sibling fields.
        Form rootForm = FormUtil.findRootForm(element);
        LogUtil.info(CLASS_NAME, "rootForm = "
                + (rootForm == null ? "null" : rootForm.getPropertyString(FormUtil.PROPERTY_ID)));
        if (rootForm != null) {
            Element districtEl = FormUtil.findElement("district", rootForm, formData);
            Element zoneEl     = FormUtil.findElement("agroEcologicalZone", rootForm, formData);
            LogUtil.info(CLASS_NAME, "findElement: district=" + (districtEl == null ? "null" : "found")
                    + " zone=" + (zoneEl == null ? "null" : "found"));
            if (districtEl != null) district = FormUtil.getElementPropertyValue(districtEl, formData);
            if (zoneEl != null)     zone     = FormUtil.getElementPropertyValue(zoneEl, formData);
        }
        LogUtil.info(CLASS_NAME, "after strategy 1: district=" + district + " zone=" + zone);

        // Strategy 2: read directly from the request parameters submitted by the
        // user. After a per-tab Next-click, the rendering response still has
        // access to the submitted form fields via the FormData parameter map.
        if (isBlank(district) || isBlank(zone)) {
            String d = formData == null ? null : formData.getRequestParameter("district");
            String z = formData == null ? null : formData.getRequestParameter("agroEcologicalZone");
            LogUtil.info(CLASS_NAME, "strategy 2 (request params): district=" + d + " zone=" + z);
            if (isBlank(district) && !isBlank(d)) district = d;
            if (isBlank(zone)     && !isBlank(z)) zone     = z;
        }

        // Strategy 3: read from DB by looking up the most-recent parcelLocation
        // row whose c_parent_id matches anything in our existing parcelGeometry
        // (if any). Useful when neither the wizard tree nor the request params
        // expose the sibling values.
        if (isBlank(district) || isBlank(zone)) {
            String parentId = null;
            if (rs != null && !rs.isEmpty()) {
                parentId = rs.get(0).getProperty("parent_id");
            }
            LogUtil.info(CLASS_NAME, "strategy 3: parent_id from existing geom row = " + parentId);
            if (!isBlank(parentId)) {
                try {
                    FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
                    FormRowSet locRows = dao.find("parcelLocation", "parcelLocation",
                            "WHERE e.customProperties.parent_id = ?",
                            new Object[]{parentId}, null, false, null, null);
                    if (locRows != null && !locRows.isEmpty()) {
                        FormRow loc = locRows.get(0);
                        if (isBlank(district)) {
                            district = firstNonBlank(loc.getProperty("district"),
                                                     loc.getProperty("c_district"));
                        }
                        if (isBlank(zone)) {
                            zone = firstNonBlank(loc.getProperty("agroEcologicalZone"),
                                                 loc.getProperty("agroecologicalzone"),
                                                 loc.getProperty("c_agroecologicalzone"));
                        }
                        LogUtil.info(CLASS_NAME, "strategy 3 hit: district=" + district + " zone=" + zone);
                    }
                } catch (Throwable t) {
                    LogUtil.error(CLASS_NAME, t, "strategy 3 DB lookup failed");
                }
            }
        }

        if (isBlank(district) || isBlank(zone)) {
            LogUtil.info(CLASS_NAME, "no district/zone found via any strategy — skipping enrichment");
            return rs;
        }

        // If the loaded row already has auto_center, don't overwrite. Treat
        // any existing value as authoritative (e.g. the citizen may have
        // captured a polygon and the centroid was set at that time).
        if (rs != null && !rs.isEmpty()) {
            FormRow existing = rs.get(0);
            String curLat = existing.getProperty("auto_center_lat");
            String curLon = existing.getProperty("auto_center_lon");
            if (curLat != null && !curLat.isEmpty()
                    && curLon != null && !curLon.isEmpty()) {
                return rs;
            }
        }

        // Look up the centroid for this (district, zone) pair.
        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        FormRowSet zoneRows;
        try {
            zoneRows = dao.find(ZONE_LOOKUP_FORM, ZONE_LOOKUP_FORM,
                    "WHERE e.customProperties.district = ? AND e.customProperties.agro_zone = ?",
                    new Object[]{district, zone},
                    null, false, null, null);
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "centroid lookup failed for ("
                    + district + ", " + zone + ")");
            return rs;
        }

        if (zoneRows == null || zoneRows.isEmpty()) {
            // No matching centroid in MD.95 — leave the GIS widget with its
            // built-in fallback. This is normal (e.g. lowlands_qachas-nek
            // doesn't exist).
            return rs;
        }

        FormRow zoneRow = zoneRows.get(0);
        String centroidLat = zoneRow.getProperty("centroid_lat");
        String centroidLon = zoneRow.getProperty("centroid_lon");
        if (isBlank(centroidLat) || isBlank(centroidLon)) {
            return rs;
        }

        // Inject. If we have an existing row, augment it; otherwise synthesise
        // a stub. The GIS widget reads auto_center_lat/lon HiddenFields from
        // the form's render-state — it doesn't care whether the row is in DB.
        FormRow target;
        if (rs == null || rs.isEmpty()) {
            target = new FormRow();
            rs = new FormRowSet();
            rs.add(target);
        } else {
            target = rs.get(0);
        }
        target.setProperty("auto_center_lat", centroidLat);
        target.setProperty("auto_center_lon", centroidLon);

        LogUtil.info(CLASS_NAME, "injected auto_center for ("
                + district + ", " + zone + ") = (" + centroidLat + ", " + centroidLon + ")");
        return rs;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String firstNonBlank(String... vs) {
        if (vs == null) return null;
        for (String v : vs) {
            if (!isBlank(v)) return v;
        }
        return null;
    }
}
