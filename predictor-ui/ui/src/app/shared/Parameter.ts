export type Parameter = {
  id: string;
  label: string;
  type: 'text' | 'boolean' | 'select' | 'openFile' | 'button';
  required?: boolean;
  options?: { id: string, label: string }[];
  defaultValue?: string;
};
