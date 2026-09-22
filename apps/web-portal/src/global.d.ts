import 'vue'
declare module 'vue' {
  interface ComponentCustomProperties {
    $zh: (value: unknown) => string
    $zhKey: (value: string) => string
  }
}
export {}
