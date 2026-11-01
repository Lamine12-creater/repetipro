import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useMemo, useState } from "react";
import { Modal, Pressable, ScrollView, Text, TextInput, View } from "react-native";
import { ScreenContainer } from "@/components/screen-container";
import { EmptyState, IconButton, PageTitle, PrimaryButton, StudentRow } from "@/components/repetipro-ui";
import { demoStudents, formatFcfa, type Student } from "@/lib/repetipro-data";
import { useColors } from "@/hooks/use-colors";

export default function StudentsScreen() {
  const colors = useColors();
  const [students, setStudents] = useState<Student[]>(demoStudents);
  const [query, setQuery] = useState("");
  const [modalVisible, setModalVisible] = useState(false);
  const [name, setName] = useState("");
  const [className, setClassName] = useState("");
  const [fee, setFee] = useState("");
  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return students;
    return students.filter((student) => `${student.name} ${student.className} ${student.subject}`.toLowerCase().includes(normalized));
  }, [query, students]);

  function addStudent() {
    if (!name.trim() || !className.trim() || !fee.trim()) return;
    const initials = name.trim().split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
    setStudents((current) => [...current, {
      id: `student-${Date.now()}`,
      name: name.trim(),
      className: className.trim(),
      subject: "À préciser",
      monthlyFee: Number(fee.replace(/\D/g, "")) || 0,
      parentPhone: "",
      initials,
      tone: "teal",
    }]);
    setName("");
    setClassName("");
    setFee("");
    setModalVisible(false);
  }

  return (
    <ScreenContainer className="px-5 pt-4">
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 28 }}>
        <PageTitle eyebrow="Suivi" title="Vos élèves" subtitle={`${students.length} élèves actifs`} action={<IconButton icon="person-add" label="Ajouter un élève" onPress={() => setModalVisible(true)} />} />

        <View className="mb-5 flex-row items-center gap-3 rounded-2xl border border-border bg-surface px-4">
          <MaterialIcons name="search" size={21} color={colors.muted} />
          <TextInput
            value={query}
            onChangeText={setQuery}
            placeholder="Rechercher un élève"
            placeholderTextColor={colors.muted}
            className="h-12 flex-1 text-sm text-foreground"
            returnKeyType="search"
          />
          {query ? <Pressable onPress={() => setQuery("")}><MaterialIcons name="close" size={19} color={colors.muted} /></Pressable> : null}
        </View>

        <View className="mb-5 flex-row items-center justify-between rounded-2xl border border-teal-200 bg-teal-50 px-4 py-3">
          <View className="flex-row items-center gap-3">
            <MaterialIcons name="verified-user" size={21} color="#0F766E" />
            <View>
              <Text className="font-bold text-teal-900">Formule Solo</Text>
              <Text className="mt-0.5 text-xs text-teal-700">{students.length} élèves actifs · sans limite</Text>
            </View>
          </View>
          <Text className="text-sm font-bold text-teal-800">Actif</Text>
        </View>

        {filtered.length ? (
          <View className="rounded-2xl border border-border bg-surface px-4">
            {filtered.map((student) => <StudentRow key={student.id} student={student} trailing={<Text className="text-xs font-bold text-muted">{formatFcfa(student.monthlyFee)}</Text>} />)}
          </View>
        ) : (
          <EmptyState icon="person-search" title="Aucun élève trouvé" body="Essayez un autre nom ou ajoutez un nouvel élève." />
        )}

        <View className="mt-5">
          <PrimaryButton label="Ajouter un élève" icon="add" onPress={() => setModalVisible(true)} />
        </View>
        <Text className="mt-5 text-center text-xs text-muted">Les données affichées sont locales pour cet aperçu.</Text>
      </ScrollView>

      <Modal visible={modalVisible} transparent animationType="slide" onRequestClose={() => setModalVisible(false)}>
        <View className="flex-1 justify-end bg-black/35">
          <View className="rounded-t-3xl bg-background px-5 pb-8 pt-5">
            <View className="mb-5 flex-row items-center justify-between">
              <View>
                <Text className="text-xl font-bold text-foreground">Nouvel élève</Text>
                <Text className="mt-1 text-sm text-muted">Ajoutez les informations essentielles.</Text>
              </View>
              <IconButton icon="close" label="Fermer" onPress={() => setModalVisible(false)} />
            </View>
            <Field label="Nom complet" value={name} onChangeText={setName} placeholder="Ex. Aïcha Mensah" />
            <Field label="Classe" value={className} onChangeText={setClassName} placeholder="Ex. 3e" />
            <Field label="Tarif mensuel en FCFA" value={fee} onChangeText={setFee} placeholder="Ex. 25000" keyboardType="numeric" />
            <PrimaryButton label="Enregistrer l’élève" onPress={addStudent} />
          </View>
        </View>
      </Modal>
    </ScreenContainer>
  );
}

function Field({ label, value, onChangeText, placeholder, keyboardType = "default" }: { label: string; value: string; onChangeText: (value: string) => void; placeholder: string; keyboardType?: "default" | "numeric" }) {
  const colors = useColors();
  return (
    <View className="mb-4">
      <Text className="mb-1.5 text-sm font-semibold text-foreground">{label}</Text>
      <TextInput value={value} onChangeText={onChangeText} placeholder={placeholder} placeholderTextColor={colors.muted} keyboardType={keyboardType} className="h-12 rounded-xl border border-border bg-surface px-3 text-foreground" />
    </View>
  );
}
