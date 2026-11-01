import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { ScrollView, Text, TouchableOpacity, View } from "react-native";
import { ScreenContainer } from "@/components/screen-container";
import { PageTitle, PrimaryButton, StatusPill, StudentAvatar } from "@/components/repetipro-ui";
import { demoInvoices, demoLessons, demoStudents, formatFcfa, formatToday } from "@/lib/repetipro-data";

export default function HomeScreen() {
  const router = useRouter();
  const todayLessons = demoLessons.filter((lesson) => lesson.dateLabel === "Aujourd’hui");
  const outstanding = demoInvoices.filter((invoice) => invoice.status !== "Payée");
  const amountToCollect = outstanding.reduce((total, invoice) => total + invoice.amount, 0);
  const collected = demoInvoices.filter((invoice) => invoice.status === "Payée").reduce((total, invoice) => total + invoice.amount, 0);
  const totalBilled = demoInvoices.reduce((total, invoice) => total + invoice.amount, 0);
  const progress = totalBilled ? Math.round((collected / totalBilled) * 100) : 0;

  return (
    <ScreenContainer className="px-5 pt-4">
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 28 }}>
        <PageTitle eyebrow="RépétiPro" title="Bonjour, Rodrigue" subtitle={formatToday()} action={<View className="h-11 w-11 items-center justify-center rounded-2xl bg-primary"><Text className="font-bold text-white">RD</Text></View>} />

        <View className="mb-5 overflow-hidden rounded-3xl bg-primary p-5">
          <View className="flex-row items-start justify-between">
            <View className="flex-1 pr-3">
              <Text className="text-sm font-semibold text-teal-100">À encaisser</Text>
              <Text className="mt-1 text-3xl font-bold text-white">{formatFcfa(amountToCollect)}</Text>
              <Text className="mt-1 text-xs text-teal-100">{outstanding.length} facture(s) à suivre ce mois-ci</Text>
            </View>
            <View className="h-11 w-11 items-center justify-center rounded-2xl bg-white/15">
              <MaterialIcons name="account-balance-wallet" size={23} color="#FFFFFF" />
            </View>
          </View>
          <View className="mt-5">
            <View className="mb-2 flex-row justify-between">
              <Text className="text-xs font-semibold text-teal-100">Encaissement du mois</Text>
              <Text className="text-xs font-bold text-white">{progress}%</Text>
            </View>
            <View className="h-2 overflow-hidden rounded-full bg-white/20">
              <View className="h-full rounded-full bg-amber-300" style={{ width: `${progress}%` }} />
            </View>
          </View>
        </View>

        <View className="mb-6 flex-row gap-3">
          <SummaryCard icon="people" value={String(demoStudents.length)} label="élèves actifs" tone="teal" />
          <SummaryCard icon="event" value={String(todayLessons.length)} label="séances aujourd’hui" tone="amber" />
        </View>

        <View className="mb-6 flex-row gap-3">
          <PrimaryButton label="Nouvelle séance" icon="add" onPress={() => router.push("/(tabs)/sessions")} />
          <TouchableOpacity className="flex-1 items-center justify-center rounded-2xl border border-border bg-surface px-3" onPress={() => router.push("/(tabs)/students")}>
            <Text className="text-sm font-bold text-foreground">Voir les élèves</Text>
          </TouchableOpacity>
        </View>

        <View className="mb-6">
          <SectionHeading title="Aujourd’hui" action="Voir tout" />
          <View className="rounded-2xl border border-border bg-surface px-4">
            {todayLessons.map((lesson) => {
              const student = demoStudents.find((item) => item.id === lesson.studentId) ?? demoStudents[0];
              return (
                <View key={lesson.id} className="flex-row items-center gap-3 border-b border-border py-3.5 last:border-b-0">
                  <View className="w-12 items-center">
                    <Text className="text-sm font-bold text-foreground">{lesson.time}</Text>
                    <Text className="mt-0.5 text-[10px] text-muted">{lesson.duration}</Text>
                  </View>
                  <View className="h-9 w-px bg-border" />
                  <StudentAvatar student={student} size={38} />
                  <View className="min-w-0 flex-1">
                    <Text className="font-bold text-foreground" numberOfLines={1}>{lesson.studentName}</Text>
                    <Text className="mt-0.5 text-xs text-muted" numberOfLines={1}>{lesson.topic}</Text>
                  </View>
                  <StatusPill status={lesson.status} />
                </View>
              );
            })}
          </View>
        </View>

        <View>
          <SectionHeading title="À surveiller" action="Factures" />
          <View className="rounded-2xl border border-border bg-surface px-4 py-1">
            {outstanding.slice(0, 2).map((invoice) => (
              <View key={invoice.id} className="flex-row items-center gap-3 border-b border-border py-3.5 last:border-b-0">
                <View className="h-9 w-9 items-center justify-center rounded-xl bg-amber-100">
                  <MaterialIcons name="receipt-long" size={19} color="#B45309" />
                </View>
                <View className="flex-1">
                  <Text className="font-bold text-foreground">{invoice.studentName}</Text>
                  <Text className="mt-0.5 text-xs text-muted">{invoice.period} · {invoice.dueLabel}</Text>
                </View>
                <Text className="text-sm font-bold text-foreground">{formatFcfa(invoice.amount)}</Text>
              </View>
            ))}
          </View>
        </View>

        <Text className="mt-6 text-center text-xs text-muted">Aperçu RépétiPro · données de démonstration</Text>
      </ScrollView>
    </ScreenContainer>
  );
}

function SummaryCard({ icon, value, label, tone }: { icon: "people" | "event"; value: string; label: string; tone: "teal" | "amber" }) {
  return (
    <View className="flex-1 rounded-2xl border border-border bg-surface p-4">
      <View className={`mb-3 h-9 w-9 items-center justify-center rounded-xl ${tone === "teal" ? "bg-teal-100" : "bg-amber-100"}`}>
        <MaterialIcons name={icon} size={19} color={tone === "teal" ? "#0F766E" : "#B45309"} />
      </View>
      <Text className="text-2xl font-bold text-foreground">{value}</Text>
      <Text className="mt-0.5 text-xs text-muted">{label}</Text>
    </View>
  );
}

function SectionHeading({ title, action }: { title: string; action: string }) {
  return (
    <View className="mb-3 flex-row items-center justify-between">
      <Text className="text-lg font-bold text-foreground">{title}</Text>
      <Text className="text-xs font-bold text-primary">{action}</Text>
    </View>
  );
}
