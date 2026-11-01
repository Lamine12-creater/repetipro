import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { Pressable, Text, View } from "react-native";
import { useColors } from "@/hooks/use-colors";
import type { InvoiceStatus, LessonStatus, Student } from "@/lib/repetipro-data";
import { formatFcfa } from "@/lib/repetipro-data";

type IconName = React.ComponentProps<typeof MaterialIcons>["name"];

export function PageTitle({
  eyebrow,
  title,
  subtitle,
  action,
}: {
  eyebrow?: string;
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}) {
  return (
    <View className="mb-5 flex-row items-start justify-between gap-3">
      <View className="flex-1">
        {eyebrow ? <Text className="mb-1 text-xs font-bold uppercase tracking-widest text-primary">{eyebrow}</Text> : null}
        <Text className="text-3xl font-bold tracking-tight text-foreground">{title}</Text>
        {subtitle ? <Text className="mt-1 text-sm leading-5 text-muted">{subtitle}</Text> : null}
      </View>
      {action}
    </View>
  );
}

export function IconButton({
  icon,
  label,
  onPress,
}: {
  icon: IconName;
  label: string;
  onPress: () => void;
}) {
  const colors = useColors();
  return (
    <Pressable
      accessibilityLabel={label}
      onPress={onPress}
      style={({ pressed }) => ({
        width: 42,
        height: 42,
        borderRadius: 14,
        alignItems: "center",
        justifyContent: "center",
        backgroundColor: colors.surface,
        borderWidth: 1,
        borderColor: colors.border,
        opacity: pressed ? 0.7 : 1,
      })}
    >
      <MaterialIcons name={icon} size={21} color={colors.foreground} />
    </Pressable>
  );
}

export function PrimaryButton({
  label,
  icon,
  onPress,
  compact = false,
}: {
  label: string;
  icon?: IconName;
  onPress: () => void;
  compact?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => ({
        minHeight: compact ? 40 : 48,
        paddingHorizontal: compact ? 14 : 18,
        borderRadius: compact ? 12 : 15,
        backgroundColor: "#087F78",
        alignItems: "center",
        justifyContent: "center",
        flexDirection: "row",
        gap: 7,
        opacity: pressed ? 0.82 : 1,
      })}
    >
      {icon ? <MaterialIcons name={icon} size={18} color="#FFFFFF" /> : null}
      <Text className="font-bold text-white">{label}</Text>
    </Pressable>
  );
}

export function StatusPill({ status }: { status: LessonStatus | InvoiceStatus }) {
  const styles: Record<string, { backgroundColor: string; color: string }> = {
    Payée: { backgroundColor: "#DCFCE7", color: "#166534" },
    Présent: { backgroundColor: "#DCFCE7", color: "#166534" },
    "En attente": { backgroundColor: "#FEF3C7", color: "#92400E" },
    "À pointer": { backgroundColor: "#E0F2FE", color: "#075985" },
    "En retard": { backgroundColor: "#FEE2E2", color: "#991B1B" },
    Absent: { backgroundColor: "#F3F4F6", color: "#4B5563" },
  };
  const tone = styles[status];
  return (
    <View className="self-start rounded-full px-2.5 py-1" style={{ backgroundColor: tone.backgroundColor }}>
      <Text className="text-xs font-bold" style={{ color: tone.color }}>
        {status}
      </Text>
    </View>
  );
}

export function StudentAvatar({ student, size = 44 }: { student: Student; size?: number }) {
  const tones = {
    teal: "#CCFBF1",
    amber: "#FEF3C7",
    blue: "#DBEAFE",
    rose: "#FCE7F3",
  };
  const textTones = {
    teal: "#0F766E",
    amber: "#B45309",
    blue: "#1D4ED8",
    rose: "#BE185D",
  };
  return (
    <View
      className="items-center justify-center rounded-2xl"
      style={{ width: size, height: size, backgroundColor: tones[student.tone] }}
    >
      <Text className="font-bold" style={{ color: textTones[student.tone], fontSize: size * 0.29 }}>
        {student.initials}
      </Text>
    </View>
  );
}

export function StudentRow({ student, trailing }: { student: Student; trailing?: React.ReactNode }) {
  return (
    <View className="flex-row items-center gap-3 border-b border-border py-3.5">
      <StudentAvatar student={student} />
      <View className="min-w-0 flex-1">
        <Text className="font-bold text-foreground" numberOfLines={1}>{student.name}</Text>
        <Text className="mt-0.5 text-xs text-muted" numberOfLines={1}>{student.className} · {student.subject}</Text>
      </View>
      {trailing ?? <Text className="text-sm font-bold text-foreground">{formatFcfa(student.monthlyFee)}</Text>}
    </View>
  );
}

export function EmptyState({ icon, title, body }: { icon: IconName; title: string; body: string }) {
  const colors = useColors();
  return (
    <View className="items-center rounded-2xl border border-dashed border-border bg-surface px-6 py-9">
      <MaterialIcons name={icon} size={30} color={colors.muted} />
      <Text className="mt-3 text-center font-bold text-foreground">{title}</Text>
      <Text className="mt-1 text-center text-sm leading-5 text-muted">{body}</Text>
    </View>
  );
}
