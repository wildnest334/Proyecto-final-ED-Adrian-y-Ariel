/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package concurrentsortingproject; 

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Stack; // Necesario para la versión iterativa de QuickSort
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ProyectoFinalConcurrencia
 * Implementa 12 tareas concurrentes (6 algoritmos x 2 estructuras de datos)
 * para comparar la eficiencia bajo una restricción de tiempo.
 */
public class ConcurrentSortingProject {
    

    // Instancia de Random compartida para el pivote aleatorio en QuickSort
    private static final Random RANDOM = new Random();

    // --- ENUMERACIÓN DE ESTRUCTURAS DE DATOS ---
    public enum DataStructure {
        ARRAY, ARRAY_LIST
    }

    // --- ENUMERACIÓN DE ALGORITMOS ---
    public enum SortAlgorithm {
        BUBBLE_SORT("Bubble Sort (O(n))"),
        SELECTION_SORT("Selection Sort (O(n))"),
        INSERTION_SORT("Insertion Sort (O(n))"),
        MERGE_SORT("Merge Sort (O(n log n))"),
        QUICK_SORT("Quick Sort (O(n log n) - Iterativo)"), // Nombre actualizado
        COUNTING_SORT("Counting Sort (O(n + k))");

        private final String displayName;

        SortAlgorithm(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
    
    // --- CLASE PARA COMBINAR ALGORITMO + ESTRUCTURA ---
    public static class SortType {
        public final SortAlgorithm algorithm;
        public final DataStructure structure;
        public final String uniqueName;

        public SortType(SortAlgorithm algorithm, DataStructure structure) {
            this.algorithm = algorithm;
            this.structure = structure;
            this.uniqueName = String.format("%s (%s)", 
                                            algorithm.getDisplayName(), 
                                            structure.name());
        }
    }

    // --- CLASE DE CONTENEDOR DE DATOS ---
    public static class DataCollection {
        public final String name;
        public final int[] data; 

        public DataCollection(String name, int[] data) {
            this.name = name;
            this.data = data;
        }
        
        // Convierte el array primitivo a una copia de ArrayList<Integer>
        public List<Integer> getArrayListCopy() {
             return Arrays.stream(data).boxed().collect(Collectors.toCollection(ArrayList::new));
        }

        // Obtiene una copia del array primitivo
        public int[] getArrayCopy() {
            return Arrays.copyOf(data, data.length);
        }
    }

    // --- GENERADOR DE DATOS ---
    public static class DataGenerator {
        private final Random random = new Random();

        public DataCollection generate100Random() {
            int[] data = new int[100];
            for (int i = 0; i < data.length; i++) {
                data[i] = random.nextInt(10000); 
            }
            return new DataCollection("100 elementos al azar", data);
        }

        public DataCollection generate50kRandom() {
            int[] data = new int[50000];
            for (int i = 0; i < data.length; i++) {
                data[i] = random.nextInt(500000); 
            }
            return new DataCollection("50,000 elementos al azar", data);
        }

        public DataCollection generate100kRandom() {
            int[] data = new int[100000];
            for (int i = 0; i < data.length; i++) {
                data[i] = random.nextInt(1000000); 
            }
            return new DataCollection("100,000 elementos al azar", data);
        }

        public DataCollection generate100kRestricted() {
            int[] data = new int[100000];
            for (int i = 0; i < data.length; i++) {
                data[i] = random.nextInt(5) + 1; // Números entre 1 y 5
            }
            return new DataCollection("100,000 elementos restringidos (1-5)", data);
        }

        public List<DataCollection> getAllCollections() {
            List<DataCollection> collections = new ArrayList<>();
            collections.add(generate100Random());
            collections.add(generate50kRandom());
            collections.add(generate100kRandom());
            collections.add(generate100kRestricted());
            return collections;
        }
    }
//AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
    // --- TAREA CONCURRENTE (RUNNABLE) PARA EL ORDENAMIENTO ---
    public static class SortTask implements Runnable {
        private final SortType sortType;
        private final long startTimeMillis;
        private final long durationMillis;
        private final DataGenerator generator;

        // Métricas que el hilo recolectar
        private volatile long sortedCollectionsCount = 0;
        private volatile long totalExecutionTimeMillis = 0;

        public SortTask(SortType sortType, long startTimeMillis, long durationMillis, DataGenerator generator) {
            this.sortType = sortType;
            this.startTimeMillis = startTimeMillis;
            this.durationMillis = durationMillis;
            this.generator = generator;
        }

        public long getSortedCollectionsCount() { return sortedCollectionsCount; }
        public long getTotalExecutionTimeMillis() { return totalExecutionTimeMillis; }
        public SortType getSortType() { return sortType; }

        public double getAverageTimePerSort() {
            if (sortedCollectionsCount == 0) return 0;
            return (double) totalExecutionTimeMillis / sortedCollectionsCount;
        }

        @Override
        public void run() {
            List<DataCollection> collections = generator.getAllCollections();
            int collectionIndex = 0;

            // El hilo se ejecuta en bucle hasta que se cumpla la restricción de tiempo
            while (System.currentTimeMillis() < startTimeMillis + durationMillis) {
                DataCollection currentCollection = collections.get(collectionIndex % collections.size());
                long start = System.currentTimeMillis();

                try {
                    // Decide si usar Array o ArrayList
                    if (sortType.structure == DataStructure.ARRAY) {
                        int[] dataToSort = currentCollection.getArrayCopy();
                        executeArraySort(dataToSort, sortType.algorithm);
                    } else { // DataStructure.ARRAY_LIST
                        List<Integer> dataToSort = currentCollection.getArrayListCopy();
                        executeListSort(dataToSort, sortType.algorithm);
                    }
                } catch (StackOverflowError e) {
                    System.err.printf("ERROR en Hilo %s: StackOverflow (Recursión profunda). Terminando hilo.\n", sortType.uniqueName);
                    break; 
                } catch (IndexOutOfBoundsException e) { 
                    System.err.printf("ERROR en Hilo %s: Fallo de índice en la partición de QuickSort (%s). Terminando hilo.\n", sortType.uniqueName, e.getMessage());
                    break;
                } catch (Exception e) {
                    System.err.printf("ERROR en Hilo %s: Excepción inesperada (%s). Terminando hilo.\n", sortType.uniqueName, e.getMessage());
                    break;
                }

                long end = System.currentTimeMillis();
                long sortTime = end - start;

                if (end < startTimeMillis + durationMillis) {
                    totalExecutionTimeMillis += sortTime;
                    sortedCollectionsCount++;
                }

                collectionIndex++;
            }
        }
        
        // --- MÉTODO PARA EJECUTAR ALGORITMO EN ARRAY (int[]) ---
        private void executeArraySort(int[] arr, SortAlgorithm algo) {
            switch (algo) {
                case BUBBLE_SORT: bubbleSort(arr); break;
                case SELECTION_SORT: selectionSort(arr); break;
                case INSERTION_SORT: insertionSort(arr); break;
                case MERGE_SORT: mergeSort(arr, 0, arr.length - 1); break;
                case QUICK_SORT: 
                    if (arr.length > 1) quickSortIterative(arr, 0, arr.length - 1); // Usamos la versión iterativa
                    break;
                case COUNTING_SORT:
                    int maxVal = 0;
                    for (int val : arr) { if (val > maxVal) maxVal = val; }
                    countingSort(arr, maxVal);
                    break;
            }
        }

        // --- MÉTODO PARA EJECUTAR ALGORITMO EN ARRAYLIST (List<Integer>) ---
        private void executeListSort(List<Integer> list, SortAlgorithm algo) {
            switch (algo) {
                case BUBBLE_SORT: bubbleSort(list); break;
                case SELECTION_SORT: selectionSort(list); break;
                case INSERTION_SORT: insertionSort(list); break;
                case MERGE_SORT: mergeSortList(list, 0, list.size() - 1); break;
                case QUICK_SORT: 
                    if (list.size() > 1) quickSortListIterative(list, 0, list.size() - 1); // Usamos la versión iterativa
                    break;
                case COUNTING_SORT:
                    int maxVal = list.stream().mapToInt(v -> v).max().orElse(0); // Max para Counting Sort
                    countingSortList(list, maxVal);
                    break;
            }
        }
        
        
        // ------------------------------------------------------------------
        // --- IMPLEMENTACIÓN DE ALGORITMOS PARA ARRAYS PRIMITIVOS (int[]) ---
        // ------------------------------------------------------------------
        
        private void bubbleSort(int[] arr) { 
            int n = arr.length;
            for (int i = 0; i < n - 1; i++) {
                for (int j = 0; j < n - i - 1; j++) {
                    if (arr[j] > arr[j + 1]) { swap(arr, j, j + 1); }
                }
            }
        }

        private void selectionSort(int[] arr) { 
            int n = arr.length;
            for (int i = 0; i < n - 1; i++) {
                int min_idx = i;
                for (int j = i + 1; j < n; j++) {
                    if (arr[j] < arr[min_idx]) { min_idx = j; }
                }
                swap(arr, min_idx, i);
            }
        }

        private void insertionSort(int[] arr) { 
            int n = arr.length;
            for (int i = 1; i < n; ++i) {
                int key = arr[i];
                int j = i - 1;
                while (j >= 0 && arr[j] > key) {
                    arr[j + 1] = arr[j];
                    j = j - 1;
                }
                arr[j + 1] = key;
            }
        }

        private void mergeSort(int[] arr, int l, int r) { 
            if (l < r) {
                int m = (l + r) / 2;
                mergeSort(arr, l, m);
                mergeSort(arr, m + 1, r);
                merge(arr, l, m, r);
            }
        }
        private void merge(int[] arr, int l, int m, int r) { 
            int n1 = m - l + 1;
            int n2 = r - m;
            int[] L = new int[n1];
            int[] R = new int[n2];
            for (int i = 0; i < n1; ++i) L[i] = arr[l + i];
            for (int j = 0; j < n2; ++j) R[j] = arr[m + 1 + j];
            int i = 0, j = 0;
            int k = l;
            while (i < n1 && j < n2) {
                if (L[i] <= R[j]) { arr[k] = L[i]; i++; } else { arr[k] = R[j]; j++; }
                k++;
            }
            while (i < n1) { arr[k] = L[i]; i++; k++; }
            while (j < n2) { arr[k] = R[j]; j++; k++; }
        }

        /**
         * QuickSort Iterativo (No recursivo) para evitar StackOverflowError.
         */
        private void quickSortIterative(int[] arr, int low, int high) {
            Stack<Integer> stack = new Stack<>();
            stack.push(low);
            stack.push(high);

            while (!stack.isEmpty()) {
                high = stack.pop();
                low = stack.pop();

                if (low < high) {
                    // Selecciona un pivote aleatorio
                    int pivotIndex = low + RANDOM.nextInt(high - low + 1);
                    swap(arr, pivotIndex, high); 
                    
                    int pi = partition(arr, low, high);

                    // Empuja las sub-arrays a la pila para el siguiente proceso
                    // Priorizamos el sub-array más grande para evitar StackOverflow (aunque ya es iterativo, es buena práctica)
                    if (pi - 1 - low > high - (pi + 1)) {
                        stack.push(low);
                        stack.push(pi - 1);
                        stack.push(pi + 1);
                        stack.push(high);
                    } else {
                        stack.push(pi + 1);
                        stack.push(high);
                        stack.push(low);
                        stack.push(pi - 1);
                    }
                }
            }
        }

        
        private int partition(int[] arr, int low, int high) { 
            int pivot = arr[high];
            int i = (low - 1); 
            for (int j = low; j < high; j++) {
                if (arr[j] <= pivot) {
                    i++;
                    swap(arr, i, j);
                }
            }
            swap(arr, i + 1, high);
            return i + 1;
        }

        private void countingSort(int[] arr, int maxVal) { 
            if (arr.length == 0 || maxVal <= 0) return;
            int[] count = new int[maxVal + 1];
            int[] output = new int[arr.length];
            for (int i = 0; i < arr.length; i++) { if (arr[i] >= 0 && arr[i] <= maxVal) count[arr[i]]++; }
            for (int i = 1; i < count.length; i++) { count[i] += count[i - 1]; }
            for (int i = arr.length - 1; i >= 0; i--) { output[count[arr[i]] - 1] = arr[i]; count[arr[i]]--; }
            for (int i = 0; i < arr.length; i++) { arr[i] = output[i]; }
        }
        
        private void swap(int[] arr, int i, int j) {
            int temp = arr[i];
            arr[i] = arr[j];
            arr[j] = temp;
        }

        // --------------------------------------------------------------------
        // --- IMPLEMENTACIÓN DE ALGORITMOS PARA ARRAYLISTS (List<Integer>) ---
        // --------------------------------------------------------------------

        private void bubbleSort(List<Integer> list) {
            int n = list.size();
            for (int i = 0; i < n - 1; i++) {
                for (int j = 0; j < n - i - 1; j++) {
                    if (list.get(j) > list.get(j + 1)) {
                        Collections.swap(list, j, j + 1);
                    }
                }
            }
        }

        private void selectionSort(List<Integer> list) {
            int n = list.size();
            for (int i = 0; i < n - 1; i++) {
                int min_idx = i;
                for (int j = i + 1; j < n; j++) {
                    if (list.get(j) < list.get(min_idx)) {
                        min_idx = j;
                    }
                }
                Collections.swap(list, min_idx, i);
            }
        }

        private void insertionSort(List<Integer> list) {
            int n = list.size();
            for (int i = 1; i < n; ++i) {
                int key = list.get(i);
                int j = i - 1;
                while (j >= 0 && list.get(j) > key) {
                    list.set(j + 1, list.get(j));
                    j = j - 1;
                }
                list.set(j + 1, key);
            }
        }

        private void mergeSortList(List<Integer> list, int l, int r) {
            if (l < r) {
                int m = (l + r) / 2;
                mergeSortList(list, l, m);
                mergeSortList(list, m + 1, r);
                mergeList(list, l, m, r);
            }
        }

        private void mergeList(List<Integer> list, int l, int m, int r) {
            List<Integer> temp = new ArrayList<>(r - l + 1);
            int i = l, j = m + 1;

            while (i <= m && j <= r) {
                if (list.get(i) <= list.get(j)) {
                    temp.add(list.get(i));
                    i++;
                } else {
                    temp.add(list.get(j));
                    j++;
                }
            }

            while (i <= m) { temp.add(list.get(i)); i++; }
            while (j <= r) { temp.add(list.get(j)); j++; }

            for (int k = 0; k < temp.size(); k++) {
                list.set(l + k, temp.get(k));
            }
        }

        /**
         * QuickSort Iterativo (No recursivo) para evitar StackOverflowError.
         */
        private void quickSortListIterative(List<Integer> list, int low, int high) {
            Stack<Integer> stack = new Stack<>();
            stack.push(low);
            stack.push(high);

            while (!stack.isEmpty()) {
                high = stack.pop();
                low = stack.pop();

                if (low < high) {
                    // Selecciona un pivote aleatorio
                    int pivotIndex = low + RANDOM.nextInt(high - low + 1);
                    Collections.swap(list, pivotIndex, high); 
                    
                    int pi = partitionList(list, low, high);

                    // Empuja las sub-arrays a la pila para el siguiente proceso
                    if (pi - 1 - low > high - (pi + 1)) {
                        stack.push(low);
                        stack.push(pi - 1);
                        stack.push(pi + 1);
                        stack.push(high);
                    } else {
                        stack.push(pi + 1);
                        stack.push(high);
                        stack.push(low);
                        stack.push(pi - 1);
                    }
                }
            }
        }

       
        private int partitionList(List<Integer> list, int low, int high) {
            int pivot = list.get(high);
            int i = (low - 1); 

            for (int j = low; j < high; j++) {
                if (list.get(j) <= pivot) {
                    i++;
                    Collections.swap(list, i, j);
                }
            }
            Collections.swap(list, i + 1, high);
            return i + 1;
        }

        private void countingSortList(List<Integer> list, int maxVal) {
             if (list.isEmpty() || maxVal <= 0) return;

            int[] count = new int[maxVal + 1];
            
            // 1. Conteo de frecuencia
            for (int val : list) {
                if (val >= 0 && val <= maxVal) count[val]++;
            }

            // 2. Reconstrucción de la lista
            list.clear();
            for (int i = 0; i < count.length; i++) {
                for (int j = 0; j < count[i]; j++) {
                    list.add(i);
                }
            }
        }
    }

    // --- LÓGICA PRINCIPAL ---
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=========================================================================");
        System.out.println("         PROYECTO FINAL: ALGORITMOS CONCURRENTES (12 TAREAS)             ");
        System.out.println("=========================================================================");
        System.out.println("Adrian Razo Mandujano, Al03050102");
        System.out.println("Ariel Martínez, Al03005455");
        // 1. Solicitud de tiempo al usuario
        long durationSeconds = 0;
        while (durationSeconds <= 0) {
            System.out.print("\nPor favor, ingrese el tiempo total de ejecución (en segundos): ");
            if (scanner.hasNextLong()) {
                durationSeconds = scanner.nextLong();
                if (durationSeconds <= 0) {
                    System.out.println("El tiempo debe ser un número positivo.");
                }
            } else {
                System.out.println("Entrada inválida. Por favor, ingrese un número.");
                scanner.next(); // Limpia el buffer
            }
        }

        final long totalDurationMillis = TimeUnit.SECONDS.toMillis(durationSeconds);
        final long startTimeMillis = System.currentTimeMillis();
        final DataGenerator generator = new DataGenerator();

        // 2. Inicializar y lanzar los 12 hilos (6 Algoritmos x 2 Estructuras)
        List<SortType> sortTypes = new ArrayList<>();
        for (SortAlgorithm algo : SortAlgorithm.values()) {
            sortTypes.add(new SortType(algo, DataStructure.ARRAY)); 
            sortTypes.add(new SortType(algo, DataStructure.ARRAY_LIST));
        }
        
        List<SortTask> tasks = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (SortType type : sortTypes) {
            SortTask task = new SortTask(type, startTimeMillis, totalDurationMillis, generator);
            tasks.add(task);
            threads.add(new Thread(task, type.uniqueName));
        }

        System.out.println("\nIniciando la ejecución concurrente de las 12 tareas...");
        System.out.printf("Tiempo total de prueba: %d segundos.\n", durationSeconds);
        System.out.println("-------------------------------------------------------------------------");

        // Iniciar todos los hilos
        for (Thread thread : threads) {
            thread.start();
        }

        // 3. Esperar a que todos los hilos terminen
        for (Thread thread : threads) {
            try {
                thread.join(); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("La ejecución principal fue interrumpida.");
            }
        }

        long actualEndTimeMillis = System.currentTimeMillis();
        System.out.println("-------------------------------------------------------------------------");
        System.out.printf("Prueba finalizada! Duración real: %.2f segundos.\n\n", 
                          (actualEndTimeMillis - startTimeMillis) / 1000.0);

        // 4. Procesar y presentar resultados
        List<SortTask> sortedResults = tasks.stream()
                .filter(task -> task.getSortedCollectionsCount() > 0) 
                .sorted(Comparator.comparingDouble(SortTask::getAverageTimePerSort))
                .collect(Collectors.toList());
        
        
        // ---- esto es para identificar quien si completo las colecciones y quien es un huevon ----
        int totalCollections = generator.getAllCollections().size(); // 4
        System.out.println("\nAlgoritmos que completaron TODAS las colecciones en el tiempo:");
        
        boolean anyCompletedAll = false;
        for (SortTask task : sortedResults) {
            if (task.getSortedCollectionsCount() >= totalCollections) {
                System.out.printf(" - %s (completó %d/%d)\n", task.getSortType().uniqueName, task.getSortedCollectionsCount(), totalCollections);
                anyCompletedAll = true;
    }
}
        if (!anyCompletedAll) {
        System.out.println(" Ninguno completó todas las colecciones en el tiempo especificado.");
        }

        // --- SALIDA DEL PROGRAMA (Reporte Final) ---
        System.out.println("=========================================================================");
        System.out.println("                      REPORTE COMPARATIVO DE EFICIENCIA                  ");
        System.out.println("=========================================================================");
        
        int rank = 1;
        
        System.out.printf("| %-4s | %-45s | %-12s | %-16s |\n", 
                          "RANK", "ALGORITMO Y ESTRUCTURA", "COLECCIONES", "TIEMPO PROMEDIO");
        System.out.println("|------|-----------------------------------------------|--------------|------------------|");

        for (SortTask task : sortedResults) {
            String avgTime;
            if (task.getSortedCollectionsCount() > 0) {
                avgTime = String.format("%.3f ms", task.getAverageTimePerSort());
            } else {
                avgTime = "N/A";
            }
            
            System.out.printf("| %-4d | %-45s | %-12d | %-16s |\n", 
                              rank++, 
                              task.getSortType().uniqueName, 
                              task.getSortedCollectionsCount(), 
                              avgTime);
        }
        System.out.println("|------|-----------------------------------------------|--------------|------------------|");

        // 5. Conclusiones y Comparación
        if (!sortedResults.isEmpty()) {
            System.out.println("\n CONCLUSIONES DE EFICIENCIA:");
            SortTask mostEfficient = sortedResults.get(0);
            
            System.out.printf("  - La combinación MáS eficiente (Rank #1) es: %s\n", 
                              mostEfficient.getSortType().uniqueName);
            System.out.printf("    Razón: Orden %d colecciones en promedio de %.3f ms.\n",
                              mostEfficient.getSortedCollectionsCount(), 
                              mostEfficient.getAverageTimePerSort());

            System.out.println("\n ANÁLISIS POR ESTRUCTURA DE DATOS:");
            System.out.println("    La tabla superior permite la comparación directa. En general:");
            System.out.println("    - Se espera que las versiones con ARRAY sean más rápidas debido a la eficiencia");
            System.out.println("      del acceso directo a memoria (O(1)) sobre List.get()/List.set().");
            System.out.println("    - El impacto de esta diferencia será mayor en los algoritmos O(n) que realizan");
            System.out.println("      muchas más operaciones de lectura/escritura.");
            
        } else {
            System.out.println("\n No se registraron colecciones ordenadas por ninguna tarea en el tiempo especificado.");
        }
        
        System.out.println("=========================================================================");
        scanner.close();
    }
}