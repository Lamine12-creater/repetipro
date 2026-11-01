export type Student = {
  id: string;
  name: string;
  className: string;
  subject: string;
  monthlyFee: number;
  parentPhone: string;
  initials: string;
  tone: "teal" | "amber" | "blue" | "rose";
};

export type LessonStatus = "À pointer" | "Présent" | "En retard" | "Absent";

export type Lesson = {
  id: string;
  studentId: string;
  studentName: string;
  dateLabel: string;
  time: string;
  duration: string;
  topic: string;
  status: LessonStatus;
};

export type InvoiceStatus = "Payée" | "En attente" | "En retard";

export type Invoice = {
  id: string;
  studentName: string;
  period: string;
  amount: number;
  dueLabel: string;
  status: InvoiceStatus;
};

export const demoStudents: Student[] = [
  {
    id: "student-1",
    name: "Fifamè Exemple",
    className: "3e",
    subject: "Maths",
    monthlyFee: 25000,
    parentPhone: "01 97 00 00 01",
    initials: "FE",
    tone: "teal",
  },
  {
    id: "student-2",
    name: "Josué Fictif",
    className: "Tle D",
    subject: "PCT",
    monthlyFee: 20000,
    parentPhone: "01 97 00 00 02",
    initials: "JF",
    tone: "amber",
  },
  {
    id: "student-3",
    name: "Sèna Modèle",
    className: "CM2",
    subject: "Français",
    monthlyFee: 16000,
    parentPhone: "01 97 00 00 03",
    initials: "SM",
    tone: "blue",
  },
  {
    id: "student-4",
    name: "Nafissatou Test",
    className: "1re C",
    subject: "Maths",
    monthlyFee: 9000,
    parentPhone: "01 97 00 00 04",
    initials: "NT",
    tone: "rose",
  },
];

export const demoLessons: Lesson[] = [
  {
    id: "lesson-1",
    studentId: "student-1",
    studentName: "Fifamè Exemple",
    dateLabel: "Aujourd’hui",
    time: "16:00",
    duration: "2 h",
    topic: "Équations du premier degré",
    status: "À pointer",
  },
  {
    id: "lesson-2",
    studentId: "student-2",
    studentName: "Josué Fictif",
    dateLabel: "Aujourd’hui",
    time: "17:30",
    duration: "2 h",
    topic: "Cinématique",
    status: "À pointer",
  },
  {
    id: "lesson-3",
    studentId: "student-3",
    studentName: "Sèna Modèle",
    dateLabel: "Demain",
    time: "19:00",
    duration: "1 h 30",
    topic: "Lecture et compréhension",
    status: "À pointer",
  },
  {
    id: "lesson-4",
    studentId: "student-1",
    studentName: "Fifamè Exemple",
    dateLabel: "Hier",
    time: "16:00",
    duration: "2 h",
    topic: "Théorème de Thalès",
    status: "Présent",
  },
];

export const demoInvoices: Invoice[] = [
  {
    id: "invoice-1",
    studentName: "Fifamè Exemple",
    period: "Octobre 2026",
    amount: 25000,
    dueLabel: "Échéance : 10 nov.",
    status: "Payée",
  },
  {
    id: "invoice-2",
    studentName: "Josué Fictif",
    period: "Octobre 2026",
    amount: 20000,
    dueLabel: "Échéance : 10 nov.",
    status: "En attente",
  },
  {
    id: "invoice-3",
    studentName: "Sèna Modèle",
    period: "Septembre 2026",
    amount: 16000,
    dueLabel: "Échéance dépassée",
    status: "En retard",
  },
  {
    id: "invoice-4",
    studentName: "Nafissatou Test",
    period: "Octobre 2026",
    amount: 9000,
    dueLabel: "Échéance : 10 nov.",
    status: "En attente",
  },
];

export function formatFcfa(amount: number) {
  return `${new Intl.NumberFormat("fr-FR").format(amount)} FCFA`;
}

export function formatToday() {
  return new Intl.DateTimeFormat("fr-FR", {
    weekday: "long",
    day: "numeric",
    month: "long",
  }).format(new Date());
}
