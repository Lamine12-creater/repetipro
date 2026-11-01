import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useMemo, useState } from "react";
import { Pressable, ScrollView, Text, View } from "react-native";
import { ScreenContainer } from "@/components/screen-container";
import { EmptyState, IconButton, PageTitle, PrimaryButton, StatusPill } from "@/components/repetipro-ui";
import { demoInvoices, formatFcfa, type Invoice, type InvoiceStatus } from "@/lib/repetipro-data";
import { useColors } from "@/hooks/use-colors";

type Filter = "Toutes" | "À suivre" | "Payées";

export default function InvoicesScreen() {
  const colors = useColors();
  const [invoices, setInvoices] = useState(demoInvoices);
  const [filter, setFilter] = useState<Filter>("Toutes");
  const visibleInvoices = useMemo(() => invoices.filter((invoice) => {
    if (filter === "À suivre") return invoice.status !== "Payée";
    if (filter === "Payées") return invoice.status === "Payée";
    return true;
  }), [filter, invoices]);
  const billed = invoices.reduce((total, invoice) => total + invoice.amount, 0);
  const collected = invoices.filter((invoice) => invoice.status === "Payée").reduce((total, invoice) => total + invoice.amount, 0);

  function markPaid(id: string) {
    setInvoices((current) => current.map((invoice) => invoice.id === id ? { ...invoice, status: "Payée" as InvoiceStatus } : invoice));
  }

  return (
    <ScreenContainer className="px-5 pt-4">
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 28 }}>
        <PageTitle eyebrow="Finances" title="Vos factures" subtitle="Suivez les paiements sans vous perdre" action={<IconButton icon="refresh" label="Actualiser les factures" onPress={() => setInvoices(demoInvoices)} />} />

        <View className="mb-5 flex-row gap-3">
          <FinanceCard label="Facturé" value={formatFcfa(billed)} icon="receipt-long" />
          <FinanceCard label="Encaissé" value={formatFcfa(collected)} icon="check-circle" positive />
        </View>

        <View className="mb-5 flex-row rounded-2xl border border-border bg-surface p-1">
          {(["Toutes", "À suivre", "Payées"] as Filter[]).map((item) => (
            <Pressable key={item} onPress={() => setFilter(item)} style={{ flex: 1, borderRadius: 12, paddingVertical: 10, alignItems: "center", backgroundColor: filter === item ? colors.primary : "transparent" }}>
              <Text className="text-xs font-bold" style={{ color: filter === item ? "#FFFFFF" : colors.muted }}>{item}</Text>
            </Pressable>
          ))}
        </View>

        {visibleInvoices.length ? visibleInvoices.map((invoice) => <InvoiceCard key={invoice.id} invoice={invoice} onMarkPaid={() => markPaid(invoice.id)} />) : <EmptyState icon="receipt-long" title="Aucune facture" body="Les factures correspondant à ce filtre apparaîtront ici." />}

        <View className="mt-5">
          <PrimaryButton label="Générer les factures du mois" icon="add" onPress={() => undefined} />
        </View>
        <Text className="mt-5 text-center text-xs text-muted">Montants affichés en FCFA · aperçu local</Text>
      </ScrollView>
    </ScreenContainer>
  );
}

function FinanceCard({ label, value, icon, positive = false }: { label: string; value: string; icon: "receipt-long" | "check-circle"; positive?: boolean }) {
  return (
    <View className="flex-1 rounded-2xl border border-border bg-surface p-4">
      <View className={`mb-3 h-9 w-9 items-center justify-center rounded-xl ${positive ? "bg-green-100" : "bg-amber-100"}`}>
        <MaterialIcons name={icon} size={19} color={positive ? "#15803D" : "#B45309"} />
      </View>
      <Text className="text-[11px] font-semibold uppercase tracking-wide text-muted">{label}</Text>
      <Text className="mt-1 text-sm font-bold text-foreground" numberOfLines={1}>{value}</Text>
    </View>
  );
}

function InvoiceCard({ invoice, onMarkPaid }: { invoice: Invoice; onMarkPaid: () => void }) {
  return (
    <View className="mb-3 rounded-2xl border border-border bg-surface p-4">
      <View className="flex-row items-start gap-3">
        <View className="h-10 w-10 items-center justify-center rounded-xl bg-amber-100"><MaterialIcons name="receipt-long" size={20} color="#B45309" /></View>
        <View className="flex-1">
          <View className="flex-row items-start justify-between gap-2">
            <View className="flex-1">
              <Text className="font-bold text-foreground">{invoice.studentName}</Text>
              <Text className="mt-0.5 text-xs text-muted">{invoice.period} · {invoice.dueLabel}</Text>
            </View>
            <Text className="text-sm font-bold text-foreground">{formatFcfa(invoice.amount)}</Text>
          </View>
          <View className="mt-3 flex-row items-center justify-between">
            <StatusPill status={invoice.status} />
            {invoice.status !== "Payée" ? <Pressable onPress={onMarkPaid} style={({ pressed }) => ({ borderRadius: 10, paddingHorizontal: 11, paddingVertical: 7, backgroundColor: pressed ? "#CCFBF1" : "#F0FDFA" })}><Text className="text-xs font-bold text-primary">Marquer payée</Text></Pressable> : <Text className="text-xs font-semibold text-green-700">Paiement enregistré</Text>}
          </View>
        </View>
      </View>
    </View>
  );
}
