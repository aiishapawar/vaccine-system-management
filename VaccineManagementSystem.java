import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Scanner;


import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

// Main project class
public class VaccineManagementSystem {

    // ---------- Exceptions ----------
    static class ValidationException extends Exception {
        public ValidationException(String message) { super(message); }
    }
    static class NotFoundException extends Exception {
        public NotFoundException(String message) { super(message); }
    }

    // ---------- OOP base and Inheritance ----------
    static class Person {
        private final String name;
        private final int age;
        private final String phone;
        public Person(String name, int age, String phone) {
            this.name = name; this.age = age; this.phone = phone;
        }
        public String getName() { return name; }
        public int getAge() { return age; }
        public String getPhone() { return phone; }
        @Override
        public String toString() { return name + " (" + age + "), Phone: " + phone; }
    }

    static class Citizen extends Person {
        private final String aadhaar;
        private boolean dose1Completed, dose2Completed;
        public Citizen(String name, int age, String phone, String aadhaar) {
            super(name, age, phone);
            this.aadhaar = aadhaar;
        }
        public String getAadhaar() { return aadhaar; }
        public boolean isDose1Completed() { return dose1Completed; }
        public boolean isDose2Completed() { return dose2Completed; }
        public void markDose1() { dose1Completed = true; }
        public void markDose2() { dose2Completed = true; }
        @Override
        public String toString() {
            return "Citizen: " + super.toString() + ", Aadhaar: " + aadhaar +
                   ", Dose1: " + (dose1Completed ? "Yes" : "No") +
                   ", Dose2: " + (dose2Completed ? "Yes" : "No");
        }
    }

    static class Staff extends Person {
        private final String staffId, role;
        public Staff(String name, int age, String phone, String staffId, String role) {
            super(name, age, phone);
            this.staffId = staffId; this.role = role;
        }
        @Override
        public String toString() {
            return "Staff: " + super.toString() + ", ID: " + staffId + ", Role: " + role;
        }
    }

    static class VaccineCenter {
        private final String centerId, name, location;
        private int dailyCapacity;
        public VaccineCenter(String centerId, String name, String location, int dailyCapacity) {
            this.centerId = centerId; this.name = name; this.location = location; this.dailyCapacity = dailyCapacity;
        }
        public String getCenterId() { return centerId; }
        public String getName() { return name; }
        public String getLocation() { return location; }
        public int getDailyCapacity() { return dailyCapacity; }
        @Override
        public String toString() {
            return centerId + " - " + name + " (" + location + "), Capacity: " + dailyCapacity;
        }
    }

    static class Appointment {
        enum Dose { DOSE1, DOSE2 }
        private final String appointmentId, aadhaar, centerId;
        private final Dose dose;
        private final LocalDate date;
        public Appointment(String appointmentId, String aadhaar, String centerId, Dose dose, LocalDate date) {
            this.appointmentId = appointmentId; this.aadhaar = aadhaar;
            this.centerId = centerId; this.dose = dose; this.date = date;
        }
        public String getAppointmentId() { return appointmentId; }
        public String getAadhaar() { return aadhaar; }
        public String getCenterId() { return centerId; }
        public Dose getDose() { return dose; }
        public LocalDate getDate() { return date; }
        @Override
        public String toString() {
            return appointmentId + "," + aadhaar + "," + centerId + "," + dose + "," + date;
        }
        public static Appointment fromCSV(String csv) {
            String[] p = csv.split(",", -1);
            return new Appointment(p[0], p[1], p[2], Dose.valueOf(p[3]), LocalDate.parse(p[4]));
        }
    }

    // ---------- Registry with File I/O + Multithreading ----------
    static class VaccineRegistry {
        private final Map<String, Citizen> citizens = new HashMap<>();
        private final Map<String, VaccineCenter> centers = new HashMap<>();
        private final Map<String, Appointment> appointments = new HashMap<>();
        private final Path citizensFile = Paths.get("citizens.csv");
        private final Path centersFile = Paths.get("centers.csv");
        private final Path appointmentsFile = Paths.get("appointments.csv");
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        public VaccineRegistry() { loadAll(); startReminderThread(); }

        public void registerCitizen(String name, int age, String phone, String aadhaar) throws ValidationException {
            if (!aadhaar.matches("\\d{12}")) throw new ValidationException("Invalid Aadhaar");
            if (!phone.matches("\\d{10}")) throw new ValidationException("Invalid Phone");
            if (age < 12) throw new ValidationException("Age must be 12+");
            citizens.put(aadhaar, new Citizen(name, age, phone, aadhaar));
            saveCitizens();
        }

        public Citizen findCitizen(String aadhaar) throws NotFoundException {
            Citizen c = citizens.get(aadhaar);
            if (c == null) throw new NotFoundException("Citizen not found");
            return c;
        }

        public void addCenter(String id, String name, String loc, int cap) {
            centers.put(id, new VaccineCenter(id, name, loc, cap));
            saveCenters();
        }

        public java.util.List<VaccineCenter> listCenters() { return new ArrayList<>(centers.values()); }

        public Appointment bookAppointment(String aadhaar, String centerId, Appointment.Dose dose, LocalDate date)
                throws ValidationException, NotFoundException {
            Citizen c = findCitizen(aadhaar);
            if (dose == Appointment.Dose.DOSE2 && !c.isDose1Completed())
                throw new ValidationException("Dose 2 requires Dose 1 first");
            String apptId = UUID.randomUUID().toString();
            Appointment appt = new Appointment(apptId, aadhaar, centerId, dose, date);
            appointments.put(apptId, appt);
            saveAppointments();
            return appt;
        }

        public java.util.List<Appointment> findAppointmentsByAadhaar(String aadhaar) {
            return appointments.values().stream()
                    .filter(a -> a.getAadhaar().equals(aadhaar))
                    .sorted(Comparator.comparing(Appointment::getDate))
                    .collect(Collectors.toList());
        }

        private void loadAll() { try { loadCitizens(); loadCenters(); loadAppointments(); } catch (Exception ignored) {} }
        private void loadCitizens() throws IOException {
            if (!Files.exists(citizensFile)) return;
            for (String line : Files.readAllLines(citizensFile)) {
                String[] p = line.split(",", -1);
                Citizen c = new Citizen(p[0], Integer.parseInt(p[1]), p[2], p[3]);
                if (Boolean.parseBoolean(p[4])) c.markDose1();
                if (Boolean.parseBoolean(p[5])) c.markDose2();
                citizens.put(c.getAadhaar(), c);
            }
        }
        private void saveCitizens() {
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(citizensFile))) {
                for (Citizen c : citizens.values()) {
                    pw.println(c.getName() + "," + c.getAge() + "," + c.getPhone() + "," + c.getAadhaar() + "," +
                            c.isDose1Completed() + "," + c.isDose2Completed());
                }
            } catch (IOException e) { System.err.println("Save error: " + e.getMessage()); }
        }
        private void loadCenters() throws IOException {
            if (!Files.exists(centersFile)) return;
            for (String line : Files.readAllLines(centersFile)) {
                String[] p = line.split(",", -1);
                centers.put(p[0], new VaccineCenter(p[0], p[1], p[2], Integer.parseInt(p[3])));
            }
        }
        private void saveCenters() {
    try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(centersFile))) {
        for (VaccineCenter c : centers.values()) {
            pw.println(c.getCenterId() + "," 
                     + c.getName() + "," 
                     + c.getLocation() + "," 
                     + c.getDailyCapacity());
        }
    } catch (IOException e) {
        System.err.println("Error saving centers: " + e.getMessage());
    }
}
            private void loadAppointments() throws IOException {
            if (!Files.exists(appointmentsFile)) return;
            for (String line : Files.readAllLines(appointmentsFile)) {
                if (line.isBlank()) continue;
                Appointment a = Appointment.fromCSV(line);
                appointments.put(a.getAppointmentId(), a);
            }
        }

        private void saveAppointments() {
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(appointmentsFile))) {
                for (Appointment a : appointments.values()) {
                    pw.println(a.toString());
                }
            } catch (IOException e) {
                System.err.println("Error saving appointments: " + e.getMessage());
            }
        }

        // Multithreading: periodic reminders
        private void startReminderThread() {
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    LocalDate tomorrow = LocalDate.now().plusDays(1);
                    java.util.List<Appointment> list = appointments.values().stream()
                            .filter(a -> a.getDate().equals(tomorrow))
                            .collect(Collectors.toList());
                    if (!list.isEmpty()) {
                        System.out.println("\n[ReminderThread] Upcoming appointments tomorrow: " + list.size());
                        for (Appointment a : list) {
                            Citizen c = citizens.get(a.getAadhaar());
                            System.out.println("[ReminderThread] " + c.getName() + " (" + c.getPhone() + ") at center "
                                    + a.getCenterId() + " for " + a.getDose() + " on " + a.getDate());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[ReminderThread] Error: " + e.getMessage());
                }
            }, 2, 10, TimeUnit.SECONDS); // start after 2s, run every 10s
        }

        public void shutdown() {
            scheduler.shutdownNow();
        }
    } // <-- closes VaccineRegistry class

    // ---------- Console UI ----------
    public static void main(String[] args) {
        VaccineRegistry registry = new VaccineRegistry();
        seedCentersIfEmpty(registry);

        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("\n=== Vaccine Management System ===");
            System.out.println("1. Register Citizen");
            System.out.println("2. List Centers");
            System.out.println("3. Book Dose 1");
            System.out.println("4. Book Dose 2");
            System.out.println("5. Show Citizen Status");
            System.out.println("6. Exit");
            System.out.print("Choose option: ");
            String ch = sc.nextLine();

            try {
                switch (ch) {
                    case "1": registerCitizen(registry, sc); break;
                    case "2": listCenters(registry); break;
                    case "3": bookDose(registry, sc, Appointment.Dose.DOSE1); break;
                    case "4": bookDose(registry, sc, Appointment.Dose.DOSE2); break;
                    case "5": showStatus(registry, sc); break;
                    case "6":
                        registry.shutdown();
                        System.out.println("Goodbye!");
                        return;
                    default:
                        System.out.println("Invalid option.");
                }
            } catch (ValidationException | NotFoundException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Unexpected error: " + e.getMessage());
            }
        }
    }

    private static void seedCentersIfEmpty(VaccineRegistry r) {
        if (r.listCenters().isEmpty()) {
            r.addCenter("C001", "City Hospital", "Solapur", 5);
            r.addCenter("C002", "Health Clinic", "Solapur East", 3);
            System.out.println("Seeded default centers.");
        }
    }

    private static void registerCitizen(VaccineRegistry r, Scanner sc) throws ValidationException {
        System.out.print("Name: ");
        String name = sc.nextLine().trim();
        System.out.print("Age: ");
        int age = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Phone (10 digits): ");
        String phone = sc.nextLine().trim();
        System.out.print("Aadhaar (12 digits): ");
        String aadhaar = sc.nextLine().trim();
        r.registerCitizen(name, age, phone, aadhaar);
        System.out.println("Citizen registered.");
    }

    private static void listCenters(VaccineRegistry r) {
        java.util.List<VaccineCenter> list = r.listCenters();
        System.out.println("--- Centers ---");
        for (VaccineCenter vc : list) System.out.println(vc);
    }

    private static void bookDose(VaccineRegistry r, Scanner sc, Appointment.Dose dose)
            throws ValidationException, NotFoundException {
        System.out.print("Aadhaar: ");
        String aadhaar = sc.nextLine().trim();
        System.out.print("Center ID: ");
        String centerId = sc.nextLine().trim();
        System.out.print("Date (YYYY-MM-DD): ");
        LocalDate date = LocalDate.parse(sc.nextLine().trim());
        Appointment appt = r.bookAppointment(aadhaar, centerId, dose, date);
        System.out.println("Booked: " + appt.getAppointmentId());
    }

    private static void showStatus(VaccineRegistry r, Scanner sc) throws NotFoundException {
        System.out.print("Aadhaar: ");
        String aadhaar = sc.nextLine().trim();
        Citizen c = r.findCitizen(aadhaar);
        System.out.println(c);
        java.util.List<Appointment> appts = r.findAppointmentsByAadhaar(aadhaar);
        if (appts.isEmpty()) System.out.println("No appointments.");
        else {
            System.out.println("Appointments:");
            for (Appointment a : appts) System.out.println(" - " + a);
        }
    }
} // <-- closes VaccineManagementSystem class

