import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useMemo, useState } from "react";
import { Pressable, ScrollView, Text, View } from "react-native";
import { ScreenContainer } from "@/components/screen-container";
import { EmptyState, IconButton, PageTitle, PrimaryButton, StatusPill, StudentAvatar } from "@/components/repetipro-ui";
import { demoLessons, demoStudents, type Lesson, type LessonStatus } from "@/lib/repetipro-data";
import { useColors } from "@/hooks/use-colors";

type Filter = "Aujourd’hui" | "À venir" | "Récentes";

export default function SessionsScreen() {
  const colors = useColors();
  const [lessons, setLessons] = useState(demoLessons);
  const [filter, setFilter] = useState<Filter>("Aujourd’hui");
  const visibleLessons = useMemo(() => lessons.filter((lesson) => {
    if (filter === "Aujourd’hui") return lesson.dateLabel === "Aujourd’hui";
    if (filter === "À venir") return lesson.dateLabel === "Aujourd’hui" || lesson.dateLabel === "Demain";
    return lesson.dateLabel === "Hier";
  }), [filter, lessons]);

  function cycleStatus(id: string) {
    setLessons((current) => current.map((lesson) => {
      if (lesson.id !== id) return lesson;
      const next: LessonStatus = lesson.status === "À pointer" ? "Présent" : lesson.status === "Présent" ? "Absent" : "À pointer";
      return { ...lesson, status: next };
    }));
  }

  return (
    <ScreenContainer className="px-5 pt-4">
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 28 }}>
        <PageTitle eyebrow="Organisation" title="Vos séances" subtitle="Gardez le fil de vos cours" action={<IconButton icon="add" label="Planifier une séance" onPress={() => undefined} />} />

        <View className="mb-5 flex-row rounded-2xl border border-border bg-surface p-1">
          {(["Aujourd’hui", "À venir", "Récentes"] as Filter[]).map((item) => (
            <Pressable key={item} onPress={() => setFilter(item)} style={{ flex: 1, borderRadius: 12, paddingVertical: 10, alignItems: "center", backgroundColor: filter === item ? colors.primary : "transparent" }}>
              <Text className="text-xs font-bold" style={{ color: filter === item ? "#FFFFFF" : colors.muted }}>{item}</Text>
            </Pressable>
          ))}
        </View>

        {visibleLessons.length ? visibleLessons.map((lesson) => {
          const student = demoStudents.find((item) => item.id === lesson.studentId) ?? demoStudents[0];
          return <LessonCard key={lesson.id} lesson={lesson} student={student} onPress={() => cycleStatus(lesson.id)} />;
        }) : <EmptyState icon="event-busy" title="Aucune séance ici" body="Planifiez une séance pour la retrouver dans votre suivi." />}

        <View className="mt-5">
          <PrimaryButton label="Planifier une séance" icon="add" onPress={() => undefined} />
        </View>
        <Text className="mt-5 text-center text-xs text-muted">Touchez une séance pour faire avancer son pointage.</Text>
      </ScrollView>
    </ScreenContainer>
  );
}

function LessonCard({ lesson, student, onPress }: { lesson: Lesson; student: typeof demoStudents[number]; onPress: () => void }) {
  return (
    <View className="mb-3 rounded-2xl border border-border bg-surface p-4">
      <View className="mb-4 flex-row items-center justify-between">
        <View className="flex-row items-center gap-2">
          <View className="h-9 w-9 items-center justify-center rounded-xl bg-primary-50"><MaterialIcons name="schedule" size={19} color="#087F78" /></View>
          <View>
            <Text className="font-bold text-foreground">{lesson.dateLabel} · {lesson.time}</Text>
            <Text className="mt-0.5 text-xs text-muted">{lesson.duration}</Text>
          </View>
        </View>
        <StatusPill status={lesson.status} />
      </View>
      <View className="flex-row items-center gap-3 border-t border-border pt-3">
        <StudentAvatar student={student} size={38} />
        <View className="flex-1">
          <Text className="font-bold text-foreground">{lesson.studentName}</Text>
          <Text className="mt-0.5 text-xs text-muted">{lesson.topic}</Text>
        </View>
        <Pressable onPress={onPress} style={({ pressed }) => ({ borderRadius: 12, paddingHorizontal: 12, paddingVertical: 9, backgroundColor: pressed ? "#CCFBF1" : "#F0FDFA" })}>
          <Text className="text-xs font-bold text-primary">Pointer</Text>
        </Pressable>
      </View>
    </View>
  );
}
