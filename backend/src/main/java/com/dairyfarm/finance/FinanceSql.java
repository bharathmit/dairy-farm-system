package com.dairyfarm.finance;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL that keeps automatic finance entries in step with the records they come from (milk sales, item purchases
 * and services, health costs, breeding fees, animal purchase/sale prices). Each source has an upsert and an
 * orphan delete. Used per farm at runtime and for every farm by the V6.1 backfill.
 */
public final class FinanceSql {

    private FinanceSql() {
    }

    private static final String COLUMNS = """
            farm_id, direction, category, amount, entry_date, entry_time, status, payment_method, counterparty,
            contact_id, alloc_kind, group_id, animal_id, quantity, unit, source_type, source_id, source_part,
            description, auto""";

    private static String upsert(String select, boolean statusFromSource, boolean paymentFromSource) {
        String status = statusFromSource ? "excluded.status" : "finance_entry.status";
        String payment = paymentFromSource ? "excluded.payment_method" : "finance_entry.payment_method";
        return "insert into finance_entry (" + COLUMNS + ")\n" + select + """

                on conflict (farm_id, source_type, source_id, source_part) where source_type is not null do update set
                  direction = excluded.direction, category = excluded.category, amount = excluded.amount,
                  entry_date = excluded.entry_date, entry_time = excluded.entry_time, status = %s,
                  payment_method = %s, counterparty = excluded.counterparty, contact_id = excluded.contact_id,
                  alloc_kind = excluded.alloc_kind, group_id = excluded.group_id, animal_id = excluded.animal_id,
                  quantity = excluded.quantity, unit = excluded.unit, description = excluded.description, updated_at = now()
                where (finance_entry.direction, finance_entry.category, finance_entry.amount, finance_entry.entry_date,
                       finance_entry.entry_time, finance_entry.status, finance_entry.payment_method, finance_entry.counterparty,
                       finance_entry.contact_id, finance_entry.alloc_kind, finance_entry.group_id, finance_entry.animal_id,
                       finance_entry.quantity, finance_entry.unit, finance_entry.description)
                  is distinct from
                      (excluded.direction, excluded.category, excluded.amount, excluded.entry_date, excluded.entry_time,
                       %s, %s, excluded.counterparty, excluded.contact_id, excluded.alloc_kind, excluded.group_id,
                       excluded.animal_id, excluded.quantity, excluded.unit, excluded.description)"""
                .formatted(status, payment, status, payment);
    }

    private static final String ANIMAL_LABEL = "a.tag || coalesce(' ' || a.name, '')";

    private static final String MILK_SALE = upsert("""
            select s.farm_id, 'INCOME',
                   case s.product when 'MILK' then 'MILK_SALES' when 'MANURE' then 'MANURE_SALES' else 'OTHER_INCOME' end,
                   s.income_amount, s.record_date, null::time,
                   case when s.paid then 'PAID' else 'PENDING' end, null, c.name, s.buyer_contact_id,
                   'FARM', null::bigint, null::bigint, s.sold_qty, s.unit, 'MILK_SALE', s.id, '',
                   left(concat_ws(' · ',
                        case s.product when 'MILK' then 'Milk' when 'MANURE' then 'Manure' else 'Other produce' end
                          || case s.session when 'AM' then ' (morning)' when 'PM' then ' (evening)' else '' end,
                        case when s.sold_qty is not null then rtrim(to_char(s.sold_qty, 'FM9999990.99'), '.') || ' ' || s.unit
                             || coalesce(' × ₹' || trim(to_char(s.rate, 'FM999990.00')), '')
                             || coalesce(' − ₹' || trim(to_char(s.discount, 'FM9999999990.00')) || ' discount', '') end), 240),
                   true
            from production_record s left join contact c on c.id = s.buyer_contact_id
            where {F:s} and s.animal_id is null and s.income_amount > 0""", true, false);

    private static final String ITEM_TXN = upsert("""
            select t.farm_id, 'EXPENSE',
                   case i.item_type when 'FEED' then 'FEED' when 'SEMEN' then 'SEMEN' when 'MEDICINE' then 'VET'
                                    when 'EQUIPMENT' then 'EQUIPMENT' else 'OTHER_EXPENSE' end,
                   t.cost, t.txn_date, t.txn_time,
                   case when t.paid = false then 'PENDING' else 'PAID' end, t.payment_method,
                   coalesce(c.name, t.mechanic_name), t.supplier_contact_id,
                   case when t.animal_id is not null then 'ANIMAL' when t.group_id is not null then 'GROUP' else 'FARM' end,
                   t.group_id, t.animal_id,
                   case when t.txn_type = 'PURCHASE' then t.quantity end,
                   case when t.txn_type = 'PURCHASE' then i.unit end,
                   'ITEM_TXN', t.id, '',
                   left(case when t.txn_type = 'SERVICE' then i.name || ' · service'
                             else i.name || ' ' || rtrim(to_char(t.quantity, 'FM9999990.99'), '.') || ' ' || lower(i.unit) end, 240),
                   true
            from item_txn t join item i on i.id = t.item_id left join contact c on c.id = t.supplier_contact_id
            where {F:t} and t.txn_type in ('PURCHASE', 'SERVICE') and t.cost > 0""", true, true);

    private static final String HEALTH = upsert("""
            select e.farm_id, 'EXPENSE', 'VET', e.cost, e.event_date, e.event_time, 'PAID', null, c.name, e.contact_id,
                   'ANIMAL', null::bigint, e.animal_id, null::numeric, null, 'HEALTH', e.id, '',
                   left(%s || ' · ' || initcap(lower(e.sub_type))
                        || coalesce(' · ' || nullif(e.details ->> 'diagnosis', ''), '')
                        || coalesce(' · ' || nullif(e.details ->> 'medicine', ''), ''), 240),
                   true
            from farm_event e join animal a on a.id = e.animal_id left join contact c on c.id = e.contact_id
            where {F:e} and e.event_type = 'HEALTH' and e.cost > 0""".formatted(ANIMAL_LABEL), false, false);

    private static String breedingPart(String part, String amount, String date, String contact, String what,
                                       boolean statusFromFee) {
        return """
                select b.farm_id, 'EXPENSE', 'BREEDING', b.%s, %s, null::time, %s, null, c.name, b.%s,
                       'ANIMAL', null::bigint, b.dam_id, null::numeric, null, 'BREEDING', b.id, '%s',
                       left(%s || ' · %s', 240), true
                from breeding_record b join animal a on a.id = b.dam_id left join contact c on c.id = b.%s
                where {F:b} and b.%s > 0 and %s is not null""".formatted(amount, date,
                statusFromFee ? "case when b.fee_paid = false then 'PENDING' else 'PAID' end" : "'PAID'",
                contact, part, ANIMAL_LABEL, what, contact, amount, date);
    }

    private static final String BREEDING_FEES = upsert(
            breedingPart("TECH_FEE", "technician_fee", "b.service_date", "technician_contact_id", "AI technician fee", true)
                    + "\nunion all\n"
                    + breedingPart("BULL_FEE", "bull_fee", "b.service_date", "bull_owner_contact_id", "bull service fee", true),
            true, false);

    private static final String BREEDING_PD = upsert(
            breedingPart("PD", "preg_check_cost", "coalesce(b.preg_check_date, b.service_date)",
                    "preg_checked_by_contact_id", "pregnancy check", false), false, false);

    private static final String ANIMAL = upsert("""
            select a.farm_id, 'EXPENSE', 'ANIMAL_PURCHASE', a.purchase_price,
                   coalesce(a.acquisition_date, a.created_at::date), null::time, 'PAID', null, null, null::bigint,
                   'ANIMAL', null::bigint, a.id, null::numeric, null, 'ANIMAL', a.id, 'PURCHASE',
                   left(%1$s || ' · bought', 240), true
            from animal a where {F:a} and a.purchase_price > 0
            union all
            select a.farm_id, 'INCOME', 'ANIMAL_SALES', a.exit_price, a.exit_date, null::time, 'PAID', null,
                   a.exit_party, null::bigint, 'ANIMAL', null::bigint, a.id, null::numeric, null, 'ANIMAL', a.id, 'SALE',
                   left(%1$s || ' · sold', 240), true
            from animal a where {F:a} and a.status = 'SOLD' and a.exit_price > 0 and a.exit_date is not null"""
            .formatted(ANIMAL_LABEL), false, false);

    private static final List<String> ORPHANS = List.of(
            """
            delete from finance_entry e where {F:e} and e.source_type = 'MILK_SALE' and not exists (
              select 1 from production_record s where s.id = e.source_id and s.animal_id is null and s.income_amount > 0)""",
            """
            delete from finance_entry e where {F:e} and e.source_type = 'ITEM_TXN' and not exists (
              select 1 from item_txn t where t.id = e.source_id and t.txn_type in ('PURCHASE', 'SERVICE') and t.cost > 0)""",
            """
            delete from finance_entry e where {F:e} and e.source_type = 'HEALTH' and not exists (
              select 1 from farm_event s where s.id = e.source_id and s.event_type = 'HEALTH' and s.cost > 0)""",
            """
            delete from finance_entry e where {F:e} and e.source_type = 'BREEDING' and not exists (
              select 1 from breeding_record b where b.id = e.source_id and (
                (e.source_part = 'TECH_FEE' and b.technician_fee > 0 and b.service_date is not null)
                or (e.source_part = 'BULL_FEE' and b.bull_fee > 0 and b.service_date is not null)
                or (e.source_part = 'PD' and b.preg_check_cost > 0
                    and coalesce(b.preg_check_date, b.service_date) is not null)))""",
            """
            delete from finance_entry e where {F:e} and e.source_type = 'ANIMAL' and not exists (
              select 1 from animal a where a.id = e.source_id and (
                (e.source_part = 'PURCHASE' and a.purchase_price > 0)
                or (e.source_part = 'SALE' and a.status = 'SOLD' and a.exit_price > 0 and a.exit_date is not null)))""");

    /** Statements for one farm (named parameter :farm), or for every farm when {@code allFarms}. */
    public static List<String> statements(boolean allFarms) {
        List<String> out = new ArrayList<>();
        out.addAll(ORPHANS);
        out.addAll(List.of(MILK_SALE, ITEM_TXN, HEALTH, BREEDING_FEES, BREEDING_PD, ANIMAL));
        return out.stream().map(s -> s.replaceAll("\\{F:(\\w+)}", allFarms ? "true" : "$1.farm_id = :farm")).toList();
    }
}
